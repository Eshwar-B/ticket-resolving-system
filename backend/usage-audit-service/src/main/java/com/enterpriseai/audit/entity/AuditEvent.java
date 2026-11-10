package com.enterpriseai.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private String resourceId;

    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditEvent() {}

    public AuditEvent(String tenantId, String eventType, String actorId, String resourceType, String resourceId, String payload, String traceId) {
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.traceId = traceId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
}
