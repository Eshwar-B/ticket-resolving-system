package com.enterpriseai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentUploadRequest(
        @NotBlank(message = "Document title is required")
        String title,

        @NotBlank(message = "Document content is required")
        String content,

        String sourceType,
        String[] permissions
) {}
