package com.enterpriseai.ticket.dto;

import com.enterpriseai.ticket.domain.CommentVisibility;
import com.enterpriseai.ticket.entity.TicketComment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single comment.
 */
public record CommentResponse(
        UUID id,
        String ticketId,
        String authorId,
        String authorName,
        CommentVisibility visibility,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CommentResponse from(TicketComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId().toString(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getVisibility(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}

