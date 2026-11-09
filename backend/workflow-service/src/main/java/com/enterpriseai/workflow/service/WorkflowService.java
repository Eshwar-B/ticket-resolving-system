package com.enterpriseai.workflow.service;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.workflow.dto.ApprovalDecisionRequest;
import com.enterpriseai.workflow.dto.ApprovalResponse;
import com.enterpriseai.workflow.dto.CreateApprovalRequest;
import com.enterpriseai.workflow.entity.ApprovalRequest;
import com.enterpriseai.workflow.repository.ApprovalRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final String WORKFLOW_TOPIC = "workflow.events.v1";

    private final ApprovalRequestRepository approvalRepository;
    private final Optional<KafkaTemplate<String, Object>> kafkaTemplate;

    public WorkflowService(ApprovalRequestRepository approvalRepository,
                           Optional<KafkaTemplate<String, Object>> kafkaTemplate) {
        this.approvalRepository = approvalRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public ApprovalResponse createApproval(CreateApprovalRequest request) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        ApprovalRequest approval = new ApprovalRequest();
        approval.setTenantId(tenantId);
        approval.setTicketId(request.ticketId());
        approval.setActionType(request.actionType());
        approval.setPayload(request.payload() != null ? request.payload() : "{}");
        approval.setStatus(ApprovalRequest.ApprovalStatus.PENDING);
        approval.setRequester(request.requester() != null ? request.requester() : "ai-supervisor");

        ApprovalRequest saved = approvalRepository.save(approval);
        log.info("Created approval request: id={} tenant={} ticket={} action={}",
                saved.getId(), tenantId, request.ticketId(), request.actionType());

        emitEvent("workflow.approval.requested.v1", tenantId, saved);
        return ApprovalResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> getApprovalsForTicket(UUID ticketId) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        return approvalRepository.findByTenantIdAndTicketIdOrderByCreatedAtDesc(tenantId, ticketId)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> getPendingApprovals() {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        return approvalRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, ApprovalRequest.ApprovalStatus.PENDING)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    @Transactional
    public ApprovalResponse approve(UUID id, ApprovalDecisionRequest decision) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        ApprovalRequest request = approvalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found: " + id));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Cannot approve request in status: " + request.getStatus());
        }

        request.setStatus(ApprovalRequest.ApprovalStatus.APPROVED);
        request.setApprovedBy(decision.approver());
        request.setDecisionNote(decision.note());

        ApprovalRequest saved = approvalRepository.save(request);
        log.info("Approval GRANTED: id={} tenant={} approver={}", id, tenantId, decision.approver());

        emitEvent("workflow.approval.granted.v1", tenantId, saved);
        return ApprovalResponse.from(saved);
    }

    @Transactional
    public ApprovalResponse reject(UUID id, ApprovalDecisionRequest decision) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        ApprovalRequest request = approvalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found: " + id));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Cannot reject request in status: " + request.getStatus());
        }

        request.setStatus(ApprovalRequest.ApprovalStatus.REJECTED);
        request.setApprovedBy(decision.approver());
        request.setDecisionNote(decision.note());

        ApprovalRequest saved = approvalRepository.save(request);
        log.info("Approval REJECTED: id={} tenant={} approver={}", id, tenantId, decision.approver());

        emitEvent("workflow.approval.denied.v1", tenantId, saved);
        return ApprovalResponse.from(saved);
    }

    private void emitEvent(String eventType, String tenantId, ApprovalRequest request) {
        kafkaTemplate.ifPresent(kt -> {
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("eventId", UUID.randomUUID().toString());
                event.put("eventType", eventType);
                event.put("tenantId", tenantId);
                event.put("approvalId", request.getId().toString());
                event.put("ticketId", request.getTicketId().toString());
                event.put("actionType", request.getActionType());
                event.put("status", request.getStatus().name());
                event.put("payload", request.getPayload());
                kt.send(WORKFLOW_TOPIC, tenantId, event);
                log.debug("Published event '{}' to topic '{}' with key '{}'", eventType, WORKFLOW_TOPIC, tenantId);
            } catch (Exception e) {
                log.warn("Failed to publish Kafka event '{}': {}", eventType, e.getMessage());
            }
        });
    }
}
