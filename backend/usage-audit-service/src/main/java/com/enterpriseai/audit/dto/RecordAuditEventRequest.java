package com.enterpriseai.audit.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordAuditEventRequest(
    @NotBlank String eventType,
    @NotBlank String actorId,
    @NotBlank String resourceType,
    @NotBlank String resourceId,
    String payload,
    String traceId
) {}
