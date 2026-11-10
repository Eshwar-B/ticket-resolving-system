package com.enterpriseai.audit.dto;

import java.util.Map;

public record UsageSummaryResponse(
    String tenantId,
    Map<String, Long> quantityByService,
    long totalRecords
) {}
