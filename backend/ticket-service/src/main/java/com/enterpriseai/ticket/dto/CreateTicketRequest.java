package com.enterpriseai.ticket.dto;

import com.enterpriseai.ticket.domain.TicketCategory;
import com.enterpriseai.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new ticket (POST /api/v1/tickets).
 */
public record CreateTicketRequest(

        @NotBlank(message = "customerId is required")
        String customerId,

        @NotNull(message = "priority is required")
        TicketPriority priority,

        @NotNull(message = "category is required")
        TicketCategory category,

        @NotBlank(message = "subject is required")
        @Size(min = 5, max = 500, message = "subject must be 5-500 characters")
        String subject,

        @NotBlank(message = "description is required")
        @Size(min = 10, max = 10_000, message = "description must be 10-10000 characters")
        String description,

        String[] tags,

        String externalRef,

        /** Client-supplied idempotency key (UUID recommended). */
        String idempotencyKey
) {}

