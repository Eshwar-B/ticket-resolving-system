package com.enterpriseai.ticket.entity;

import com.enterpriseai.ticket.domain.SlaTier;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_sla")
public class TicketSla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false, unique = true)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlaTier tier = SlaTier.STANDARD;

    @Column(name = "first_response_due", nullable = false)
    private OffsetDateTime firstResponseDue;

    @Column(name = "first_response_at")
    private OffsetDateTime firstResponseAt;

    @Column(name = "first_response_met")
    private Boolean firstResponseMet;

    @Column(name = "resolution_due", nullable = false)
    private OffsetDateTime resolutionDue;

    @Column(name = "resolution_met")
    private Boolean resolutionMet;

    @Column(nullable = false)
    private boolean breached = false;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "pause_duration_s", nullable = false)
    private Long pauseDurationSeconds = 0L;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters / setters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public SlaTier getTier() { return tier; }
    public void setTier(SlaTier tier) { this.tier = tier; }
    public OffsetDateTime getFirstResponseDue() { return firstResponseDue; }
    public void setFirstResponseDue(OffsetDateTime firstResponseDue) { this.firstResponseDue = firstResponseDue; }
    public OffsetDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(OffsetDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public Boolean getFirstResponseMet() { return firstResponseMet; }
    public void setFirstResponseMet(Boolean firstResponseMet) { this.firstResponseMet = firstResponseMet; }
    public OffsetDateTime getResolutionDue() { return resolutionDue; }
    public void setResolutionDue(OffsetDateTime resolutionDue) { this.resolutionDue = resolutionDue; }
    public Boolean getResolutionMet() { return resolutionMet; }
    public void setResolutionMet(Boolean resolutionMet) { this.resolutionMet = resolutionMet; }
    public boolean isBreached() { return breached; }
    public void setBreached(boolean breached) { this.breached = breached; }
    public OffsetDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(OffsetDateTime pausedAt) { this.pausedAt = pausedAt; }
    public Long getPauseDurationSeconds() { return pauseDurationSeconds; }
    public void setPauseDurationSeconds(Long pauseDurationSeconds) { this.pauseDurationSeconds = pauseDurationSeconds; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

