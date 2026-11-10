package com.enterpriseai.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_quotas")
public class TenantQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "monthly_ticket_limit", nullable = false)
    private int monthlyTicketLimit = 10000;

    @Column(name = "monthly_ai_call_limit", nullable = false)
    private int monthlyAiCallLimit = 50000;

    @Column(name = "storage_limit_mb", nullable = false)
    private int storageLimitMb = 5120;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantQuota() {}

    public TenantQuota(String tenantId, int monthlyTicketLimit, int monthlyAiCallLimit, int storageLimitMb) {
        this.tenantId = tenantId;
        this.monthlyTicketLimit = monthlyTicketLimit;
        this.monthlyAiCallLimit = monthlyAiCallLimit;
        this.storageLimitMb = storageLimitMb;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public int getMonthlyTicketLimit() { return monthlyTicketLimit; }
    public int getMonthlyAiCallLimit() { return monthlyAiCallLimit; }
    public int getStorageLimitMb() { return storageLimitMb; }
    public Instant getUpdatedAt() { return updatedAt; }
}
