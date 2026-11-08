package com.enterpriseai.ticket.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for ticket assignment (PUT /api/v1/tickets/{id}/assignment).
 */
public record AssignTicketRequest(

        @NotBlank(message = "agentId is required")
        String agentId,

        String team,

        String note
) {}

