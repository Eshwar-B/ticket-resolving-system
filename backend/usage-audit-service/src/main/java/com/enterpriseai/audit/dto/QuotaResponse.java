package com.enterpriseai.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record QuotaResponse(
    UUID id,
    String tenantId,
    int monthlyTicketLimit,
    int monthlyAiCallLimit,
    int storageLimitMb,
    Instant updatedAt
) {}
