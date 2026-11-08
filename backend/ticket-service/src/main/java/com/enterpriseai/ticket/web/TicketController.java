package com.enterpriseai.ticket.web;

import com.enterpriseai.ticket.dto.*;
import com.enterpriseai.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Ticket resource.
 */
@RestController
@RequestMapping(value = "/api/v1/tickets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tickets", description = "Multi-tenant support ticket management")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/tickets — Create a new ticket
    // -------------------------------------------------------------------------

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a ticket",
            description = "Creates a new support ticket for the authenticated tenant. Supports idempotency via Idempotency-Key.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Ticket created"),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "Idempotency key conflict",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return ticketService.createTicket(request);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/tickets — List tickets (paginated)
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(
            summary = "List tickets",
            description = "Returns a paginated list of tickets for the current tenant. Sorted by createdAt DESC.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ticket list")
            }
    )
    public PageResponse<TicketResponse> listTickets(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ticketService.listTickets(page, size);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/tickets/{id} — Get single ticket
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a ticket by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ticket found"),
                    @ApiResponse(responseCode = "404", description = "Ticket not found",
                            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    public TicketResponse getTicket(
            @Parameter(description = "Ticket UUID") @PathVariable("id") UUID id) {
        return ticketService.getTicket(id);
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/tickets/{id}/status — Transition ticket status
    // -------------------------------------------------------------------------

    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Transition ticket status",
            description = "Applies a state machine transition. Invalid transitions return 422 with allowed transitions.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status updated"),
                    @ApiResponse(responseCode = "404", description = "Ticket not found"),
                    @ApiResponse(responseCode = "422", description = "Invalid state transition")
            }
    )
    public TicketResponse transitionStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TransitionTicketRequest request,
            @RequestHeader(name = "X-Actor-Id", defaultValue = "unknown") String actorId,
            @RequestHeader(name = "X-Actor-Name", defaultValue = "Unknown") String actorName) {
        return ticketService.transitionStatus(id, request, actorId, actorName);
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/tickets/{id}/assignment — Assign ticket
    // -------------------------------------------------------------------------

    @PutMapping(value = "/{id}/assignment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Assign a ticket",
            description = "Assigns the ticket to an agent/team. Auto-transitions NEW → OPEN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Assignment updated"),
                    @ApiResponse(responseCode = "404", description = "Ticket not found")
            }
    )
    public TicketResponse assignTicket(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignTicketRequest request,
            @RequestHeader(name = "X-Actor-Id", defaultValue = "unknown") String actorId,
            @RequestHeader(name = "X-Actor-Name", defaultValue = "Unknown") String actorName) {
        return ticketService.assignTicket(id, request, actorId, actorName);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/tickets/{id}/comments — Add a comment
    // -------------------------------------------------------------------------

    @PostMapping(value = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a comment",
            description = "Adds a PUBLIC or INTERNAL comment. A PUBLIC customer reply auto-transitions from WAITING_FOR_CUSTOMER → IN_PROGRESS.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Comment created"),
                    @ApiResponse(responseCode = "404", description = "Ticket not found")
            }
    )
    public CommentResponse addComment(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AddCommentRequest request,
            @RequestHeader(name = "X-Actor-Id", defaultValue = "unknown") String authorId,
            @RequestHeader(name = "X-Actor-Name", defaultValue = "Unknown") String authorName) {
        return ticketService.addComment(id, request, authorId, authorName);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/tickets/{id}/comments — List comments
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/comments")
    @Operation(
            summary = "List comments",
            description = "Lists all non-deleted comments for a ticket in chronological order."
    )
    public List<CommentResponse> listComments(@PathVariable("id") UUID id) {
        return ticketService.listComments(id);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/tickets/{id} — Soft delete
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a ticket (soft delete)",
            description = "Marks the ticket as deleted. The record is retained in the database for audit purposes.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Ticket deleted"),
                    @ApiResponse(responseCode = "404", description = "Ticket not found")
            }
    )
    public void deleteTicket(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-Actor-Id", defaultValue = "unknown") String actorId) {
        ticketService.deleteTicket(id, actorId);
    }
}
