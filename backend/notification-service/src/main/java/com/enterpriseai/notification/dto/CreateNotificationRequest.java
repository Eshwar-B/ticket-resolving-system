package com.enterpriseai.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNotificationRequest(
    @NotBlank String userId,
    String type,
    @NotBlank String title,
    @NotBlank String body
) {}
