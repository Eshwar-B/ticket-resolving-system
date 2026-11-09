package com.enterpriseai.workflow.dto;

import com.enterpriseai.workflow.entity.ApprovalRequest;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApprovalResponse(
        UUID id,
        String tenantId,
        UUID ticketId,
        String actionType,
        String payload,
        ApprovalRequest.ApprovalStatus status,
        String requester,
        String approvedBy,
        String decisionNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ApprovalResponse from(ApprovalRequest entity) {
        return new ApprovalResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getTicketId(),
                entity.getActionType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRequester(),
                entity.getApprovedBy(),
                entity.getDecisionNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
