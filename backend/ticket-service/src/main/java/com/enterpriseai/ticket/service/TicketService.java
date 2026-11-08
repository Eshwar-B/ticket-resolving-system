package com.enterpriseai.ticket.service;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.ticket.domain.TicketStateMachine;
import com.enterpriseai.ticket.domain.TicketStatus;
import com.enterpriseai.ticket.dto.*;
import com.enterpriseai.ticket.entity.*;
import com.enterpriseai.ticket.exception.IdempotencyConflictException;
import com.enterpriseai.ticket.exception.ResourceNotFoundException;
import com.enterpriseai.ticket.repository.CustomerRepository;
import com.enterpriseai.ticket.repository.TicketCommentRepository;
import com.enterpriseai.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for ticket management.
 *
 * <p>All public methods are {@code @Transactional}; the
 * {@link com.enterpriseai.ticket.config.TenantTransactionInterceptor} AOP aspect
 * fires before each method and executes {@code SET LOCAL app.tenant_id = ?} so that
 * PostgreSQL RLS policies scope all queries to the current tenant.
 *
 * <p><strong>State machine:</strong> Every status change goes through
 * {@link TicketStateMachine#transition(TicketStatus, TicketStatus)} — arbitrary
 * status overwrites are rejected with {@link com.enterpriseai.ticket.exception.InvalidStateTransitionException}.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    // SLA hours by tier — in Phase 4 these will come from tenant configuration
    private static final long STANDARD_FIRST_RESPONSE_H = 8;
    private static final long STANDARD_RESOLUTION_H = 72;

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final TicketCommentRepository commentRepository;
    private final OutboxService outboxService;

    public TicketService(TicketRepository ticketRepository,
                         CustomerRepository customerRepository,
                         TicketCommentRepository commentRepository,
                         OutboxService outboxService) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.commentRepository = commentRepository;
        this.outboxService = outboxService;
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Creating ticket for tenant={} customer={}", tenantId, request.customerId());

        // Idempotency check
        if (request.idempotencyKey() != null) {
            ticketRepository.findByIdempotencyKey(request.idempotencyKey())
                    .ifPresent(existing -> {
                        throw new IdempotencyConflictException(request.idempotencyKey());
                    });
        }

        // Load customer (RLS-scoped — customer must belong to same tenant)
        Customer customer = customerRepository.findById(UUID.fromString(request.customerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        // Build ticket
        Ticket ticket = new Ticket();
        ticket.setTenantId(tenantId);
        ticket.setCustomer(customer);
        ticket.setPriority(request.priority());
        ticket.setCategory(request.category());
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setTags(request.tags() != null ? request.tags() : new String[0]);
        ticket.setExternalRef(request.externalRef());
        ticket.setIdempotencyKey(request.idempotencyKey());
        ticket.setStatus(TicketStatus.NEW);

        // SLA
        TicketSla sla = buildSla(tenantId, ticket);
        ticket.setSla(sla);

        // Initial history entry
        TicketHistory created = new TicketHistory();
        created.setTenantId(tenantId);
        created.setTicket(ticket);
        created.setActorId("system");
        created.setActorName("System");
        created.setEventType("TICKET_CREATED");
        created.setToValue(TicketStatus.NEW.name());
        created.setDetails("{\"source\":\"api\"}");
        ticket.getHistory().add(created);

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created: id={} number={} tenant={}", saved.getId(), saved.getTicketNumber(), tenantId);

        // Transactional Outbox: atomic event publication intent in the same DB transaction
        outboxService.recordEvent(
                tenantId,
                "ticket.created.v1",
                "ticket.created.v1",
                1,
                saved.getId().toString(),
                1,
                request.idempotencyKey(),
                safeMap(
                        "ticketId", saved.getId().toString(),
                        "ticketNumber", saved.getTicketNumber() != null ? saved.getTicketNumber() : 0L,
                        "customerId", customer.getId().toString(),
                        "customerEmail", customer.getEmail(),
                        "status", saved.getStatus().name(),
                        "priority", saved.getPriority().name(),
                        "category", saved.getCategory().name(),
                        "subject", saved.getSubject(),
                        "description", saved.getDescription()
                )
        );

        return TicketResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listTickets(int page, int size) {
        if (size > 100) size = 100;  // cap page size
        if (page < 0) page = 0;

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> ticketPage = ticketRepository.findAllActive(pageable);

        List<TicketResponse> items = ticketPage.getContent().stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());

        return new PageResponse<>(
                items,
                size,
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                page,
                ticketPage.hasNext(),
                ticketPage.hasPrevious()
        );
    }

    // -------------------------------------------------------------------------
    // STATUS TRANSITIONS
    // -------------------------------------------------------------------------

    @Transactional
    public TicketResponse transitionStatus(UUID ticketId, TransitionTicketRequest request, String actorId, String actorName) {
        String tenantId = TenantContext.requireTenantId();
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));

        TicketStatus fromStatus = ticket.getStatus();
        TicketStatus toStatus = request.targetStatus();

        // Validate transition through state machine
        TicketStateMachine.transition(fromStatus, toStatus);

        ticket.setStatus(toStatus);

        // Set terminal timestamps
        if (toStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(OffsetDateTime.now());
        } else if (toStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(OffsetDateTime.now());
        }

        // Audit history
        TicketHistory historyEntry = new TicketHistory();
        historyEntry.setTenantId(tenantId);
        historyEntry.setTicket(ticket);
        historyEntry.setActorId(actorId);
        historyEntry.setActorName(actorName);
        historyEntry.setEventType("STATUS_CHANGED");
        historyEntry.setFromValue(fromStatus.name());
        historyEntry.setToValue(toStatus.name());
        if (request.note() != null) {
            historyEntry.setDetails("{\"note\":\"" + request.note().replace("\"", "'") + "\"}");
        }
        ticket.getHistory().add(historyEntry);

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} status: {} -> {} by actor={} tenant={}", ticketId, fromStatus, toStatus, actorId, tenantId);

        // Transactional Outbox for status update
        outboxService.recordEvent(
                tenantId,
                "ticket.updated.v1",
                "ticket.updated.v1",
                1,
                saved.getId().toString(),
                2,
                null,
                safeMap(
                        "ticketId", saved.getId().toString(),
                        "ticketNumber", saved.getTicketNumber() != null ? saved.getTicketNumber() : 0L,
                        "changeType", "STATUS_CHANGED",
                        "previousState", fromStatus.name(),
                        "newState", toStatus.name(),
                        "actorId", actorId
                )
        );

        return TicketResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // ASSIGNMENT
    // -------------------------------------------------------------------------

    @Transactional
    public TicketResponse assignTicket(UUID ticketId, AssignTicketRequest request, String actorId, String actorName) {
        String tenantId = TenantContext.requireTenantId();
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));

        String previousAgent = ticket.getAssignedAgent();
        ticket.setAssignedAgent(request.agentId());
        ticket.setAssignedTeam(request.team());

        // Record assignment
        TicketAssignment assignment = new TicketAssignment();
        assignment.setTenantId(tenantId);
        assignment.setTicket(ticket);
        assignment.setAssignedTo(request.agentId());
        assignment.setAssignedBy(actorId);
        assignment.setTeam(request.team());
        assignment.setNote(request.note());
        ticket.getAssignments().add(assignment);

        // History
        TicketHistory historyEntry = new TicketHistory();
        historyEntry.setTenantId(tenantId);
        historyEntry.setTicket(ticket);
        historyEntry.setActorId(actorId);
        historyEntry.setActorName(actorName);
        historyEntry.setEventType("ASSIGNED");
        historyEntry.setFromValue(previousAgent);
        historyEntry.setToValue(request.agentId());
        ticket.getHistory().add(historyEntry);

        // Auto-transition from NEW to OPEN on first assignment
        if (ticket.getStatus() == TicketStatus.NEW) {
            TicketStateMachine.transition(TicketStatus.NEW, TicketStatus.OPEN);
            ticket.setStatus(TicketStatus.OPEN);
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} assigned to agent={} by actor={} tenant={}", ticketId, request.agentId(), actorId, tenantId);

        // Transactional Outbox for assignment
        outboxService.recordEvent(
                tenantId,
                "ticket.assigned.v1",
                "ticket.assigned.v1",
                1,
                saved.getId().toString(),
                2,
                null,
                safeMap(
                        "ticketId", saved.getId().toString(),
                        "ticketNumber", saved.getTicketNumber() != null ? saved.getTicketNumber() : 0L,
                        "assignedTo", request.agentId(),
                        "assignedBy", actorId
                )
        );

        return TicketResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // COMMENTS
    // -------------------------------------------------------------------------

    @Transactional
    public CommentResponse addComment(UUID ticketId, AddCommentRequest request, String authorId, String authorName) {
        String tenantId = TenantContext.requireTenantId();
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));

        TicketComment comment = new TicketComment();
        comment.setTenantId(tenantId);
        comment.setTicket(ticket);
        comment.setAuthorId(authorId);
        comment.setAuthorName(authorName);
        comment.setBody(request.body());
        comment.setVisibility(request.visibility());

        // If this is first public comment and ticket was waiting for customer, auto-transition
        if (request.visibility().name().equals("PUBLIC") &&
                ticket.getStatus() == TicketStatus.WAITING_FOR_CUSTOMER) {
            TicketStateMachine.transition(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.IN_PROGRESS);
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        ticket.getComments().add(comment);
        ticketRepository.save(ticket);

        log.debug("Comment added to ticket={} by author={} tenant={}", ticketId, authorId, tenantId);
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID ticketId) {
        // Verify ticket exists and belongs to tenant (RLS handles scoping)
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));

        return commentRepository.findByTicketIdAndDeletedAtIsNullOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // SOFT DELETE
    // -------------------------------------------------------------------------

    @Transactional
    public void deleteTicket(UUID ticketId, String actorId) {
        String tenantId = TenantContext.requireTenantId();
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId.toString()));

        ticket.setDeletedAt(OffsetDateTime.now());

        TicketHistory historyEntry = new TicketHistory();
        historyEntry.setTenantId(tenantId);
        historyEntry.setTicket(ticket);
        historyEntry.setActorId(actorId);
        historyEntry.setActorName(actorId);
        historyEntry.setEventType("TICKET_DELETED");
        ticket.getHistory().add(historyEntry);

        ticketRepository.save(ticket);
        log.info("Ticket {} soft-deleted by actor={} tenant={}", ticketId, actorId, tenantId);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private TicketSla buildSla(String tenantId, Ticket ticket) {
        OffsetDateTime now = OffsetDateTime.now();
        TicketSla sla = new TicketSla();
        sla.setTenantId(tenantId);
        sla.setTicket(ticket);
        sla.setTier(SlaTierResolver.resolve(ticket.getPriority()));
        sla.setFirstResponseDue(now.plusHours(STANDARD_FIRST_RESPONSE_H));
        sla.setResolutionDue(now.plusHours(STANDARD_RESOLUTION_H));
        return sla;
    }

    private java.util.Map<String, Object> safeMap(Object... entries) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            String key = (String) entries[i];
            Object val = entries[i + 1];
            if (val != null) {
                map.put(key, val);
            }
        }
        return map;
    }
}

