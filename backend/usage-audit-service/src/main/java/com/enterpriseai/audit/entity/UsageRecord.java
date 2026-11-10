package com.enterpriseai.audit.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private String service;

    @Column(nullable = false, updatable = false)
    private String operation;

    @Column(nullable = false, updatable = false)
    private long quantity = 1;

    @Column(nullable = false, updatable = false)
    private String unit = "TOKENS";

    @Column(name = "cost_estimate", nullable = false, updatable = false)
    private BigDecimal costEstimate = BigDecimal.ZERO;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    public UsageRecord() {}

    public UsageRecord(String tenantId, String service, String operation, long quantity, String unit, BigDecimal costEstimate) {
        this.tenantId = tenantId;
        this.service = service;
        this.operation = operation;
        this.quantity = quantity;
        this.unit = unit;
        this.costEstimate = costEstimate != null ? costEstimate : BigDecimal.ZERO;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getService() { return service; }
    public String getOperation() { return operation; }
    public long getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public BigDecimal getCostEstimate() { return costEstimate; }
    public Instant getRecordedAt() { return recordedAt; }
}
