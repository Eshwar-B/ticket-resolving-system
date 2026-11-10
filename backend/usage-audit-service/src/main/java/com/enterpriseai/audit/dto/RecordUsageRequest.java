package com.enterpriseai.audit.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record RecordUsageRequest(
    @NotBlank String service,
    @NotBlank String operation,
    long quantity,
    String unit,
    BigDecimal costEstimate
) {}
