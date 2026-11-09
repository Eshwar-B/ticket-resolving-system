package com.enterpriseai.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalDecisionRequest(
        @NotBlank String approver,
        String note
) {}
