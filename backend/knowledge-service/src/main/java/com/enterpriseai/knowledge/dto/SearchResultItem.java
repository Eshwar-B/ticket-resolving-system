package com.enterpriseai.knowledge.dto;

import java.util.UUID;

public record SearchResultItem(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        Integer version,
        Integer chunkIndex,
        String content,
        Double score,
        String retrievalMethod,
        String citation
) {}

