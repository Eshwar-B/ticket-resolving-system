package com.enterpriseai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record HybridSearchRequest(
        @NotBlank(message = "Search query is required")
        String query,

        Integer limit,
        Double minScore
) {}

