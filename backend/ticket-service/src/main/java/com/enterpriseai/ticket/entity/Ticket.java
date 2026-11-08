package com.enterpriseai.ticket.entity;

import com.enterpriseai.ticket.domain.TicketCategory;
import com.enterpriseai.ticket.domain.TicketPriority;
import com.enterpriseai.ticket.domain.TicketStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code tickets} table in {@code ticket_db}.
 *
 * <p><strong>RLS note:</strong> Hibernate only maps rows that are visible after
 * {@code SET LOCAL app.tenant_id = ?} has been executed by
 * {@link com.enterpriseai.ticket.config.TenantTransactionInterceptor}.
 * Never bypass that interceptor.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "ticket_number", insertable = false, updatable = false)
    private Long ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category = TicketCategory.GENERAL;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags = new String[0];

    @Column(name = "external_ref")
    private String externalRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata = "{}";

    @Column(name = "assigned_team")
    private String assignedTeam;

    @Column(name = "assigned_agent")
    private String assignedAgent;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("assignedAt DESC")
    private List<TicketAssignment> assignments = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketHistory> history = new ArrayList<>();

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private TicketSla sla;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getTicketNumber() { return ticketNumber; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(String assignedTeam) { this.assignedTeam = assignedTeam; }
    public String getAssignedAgent() { return assignedAgent; }
    public void setAssignedAgent(String assignedAgent) { this.assignedAgent = assignedAgent; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<TicketComment> getComments() { return comments; }
    public List<TicketAssignment> getAssignments() { return assignments; }
    public List<TicketHistory> getHistory() { return history; }
    public TicketSla getSla() { return sla; }
    public void setSla(TicketSla sla) { this.sla = sla; }
}

