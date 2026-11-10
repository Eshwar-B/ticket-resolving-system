package com.enterpriseai.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String tenantId,
    String userId,
    String type,
    String title,
    String body,
    boolean isRead,
    Instant createdAt,
    Instant readAt
) {}
