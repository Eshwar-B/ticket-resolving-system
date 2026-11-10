package com.enterpriseai.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record WebhookResponse(
    UUID id,
    String tenantId,
    String url,
    boolean isActive,
    Instant createdAt
) {}
