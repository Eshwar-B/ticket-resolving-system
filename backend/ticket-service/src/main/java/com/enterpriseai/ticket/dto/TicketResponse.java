package com.enterpriseai.ticket.dto;

import com.enterpriseai.ticket.domain.TicketCategory;
import com.enterpriseai.ticket.domain.TicketPriority;
import com.enterpriseai.ticket.domain.TicketStatus;
import com.enterpriseai.ticket.entity.Ticket;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for ticket operations. Maps from {@link Ticket} entity.
 */
public record TicketResponse(
        UUID id,
        Long ticketNumber,
        String tenantId,
        String customerId,
        String customerEmail,
        String customerName,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        String subject,
        String description,
        String[] tags,
        String externalRef,
        String assignedTeam,
        String assignedAgent,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        List<CommentResponse> comments
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTenantId(),
                ticket.getCustomer().getId().toString(),
                ticket.getCustomer().getEmail(),
                ticket.getCustomer().getDisplayName(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getTags(),
                ticket.getExternalRef(),
                ticket.getAssignedTeam(),
                ticket.getAssignedAgent(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                null  // comments loaded separately to avoid N+1
        );
    }
}

