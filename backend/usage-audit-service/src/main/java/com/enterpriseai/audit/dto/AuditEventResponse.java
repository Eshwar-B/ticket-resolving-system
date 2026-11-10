package com.enterpriseai.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    String tenantId,
    String eventType,
    String actorId,
    String resourceType,
    String resourceId,
    String payload,
    String traceId,
    Instant createdAt
) {}
