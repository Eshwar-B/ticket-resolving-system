package com.enterpriseai.ticket.dto;

import com.enterpriseai.ticket.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a comment (POST /api/v1/tickets/{id}/comments).
 */
public record AddCommentRequest(

        @NotBlank(message = "body is required")
        @Size(min = 1, max = 50_000, message = "body must be 1-50000 characters")
        String body,

        @NotNull(message = "visibility is required")
        CommentVisibility visibility
) {}

