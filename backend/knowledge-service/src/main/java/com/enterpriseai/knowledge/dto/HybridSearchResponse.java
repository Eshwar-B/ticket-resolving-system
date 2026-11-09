package com.enterpriseai.knowledge.dto;

import java.util.List;

public record HybridSearchResponse(
        String query,
        String tenantId,
        int totalHits,
        List<SearchResultItem> results
) {}
