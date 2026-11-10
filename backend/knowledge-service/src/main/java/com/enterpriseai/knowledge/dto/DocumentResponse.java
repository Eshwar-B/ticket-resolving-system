package com.enterpriseai.knowledge.dto;

import com.enterpriseai.knowledge.entity.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String tenantId,
        String title,
        String contentType,
        String s3Key,
        Long fileSizeBytes,
        Integer version,
        String status,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getTenantId(),
                doc.getTitle(),
                doc.getContentType(),
                doc.getS3Key(),
                doc.getFileSizeBytes(),
                doc.getVersion(),
                doc.getStatus(),
                doc.getChunks() != null ? doc.getChunks().size() : 0,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}

