package com.enterpriseai.ticket.dto;

import com.enterpriseai.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for a ticket status transition (PATCH /api/v1/tickets/{id}/status).
 */
public record TransitionTicketRequest(

        @NotNull(message = "targetStatus is required")
        TicketStatus targetStatus,

        /** Optional note explaining the transition (stored in ticket_history). */
        String note
) {}

