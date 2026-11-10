package com.enterpriseai.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateApprovalRequest(
        @NotNull UUID ticketId,
        @NotBlank String actionType,
        String payload,
        String requester
) {}

