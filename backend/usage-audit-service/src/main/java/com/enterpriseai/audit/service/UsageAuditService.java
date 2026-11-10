package com.enterpriseai.audit.service;

import com.enterpriseai.audit.dto.*;
import com.enterpriseai.audit.entity.AuditEvent;
import com.enterpriseai.audit.entity.TenantQuota;
import com.enterpriseai.audit.entity.UsageRecord;
import com.enterpriseai.audit.repository.AuditEventRepository;
import com.enterpriseai.audit.repository.TenantQuotaRepository;
import com.enterpriseai.audit.repository.UsageRecordRepository;
import com.enterpriseai.common.context.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsageAuditService {

    private final AuditEventRepository auditRepository;
    private final UsageRecordRepository usageRepository;
    private final TenantQuotaRepository quotaRepository;

    public UsageAuditService(AuditEventRepository auditRepository,
                             UsageRecordRepository usageRepository,
                             TenantQuotaRepository quotaRepository) {
        this.auditRepository = auditRepository;
        this.usageRepository = usageRepository;
        this.quotaRepository = quotaRepository;
    }

    @Transactional
    public AuditEventResponse recordAuditEvent(RecordAuditEventRequest req) {
        String tenantId = TenantContext.getRequiredTenantId();
        AuditEvent event = new AuditEvent(
            tenantId,
            req.eventType(),
            req.actorId(),
            req.resourceType(),
            req.resourceId(),
            req.payload(),
            req.traceId()
        );
        AuditEvent saved = auditRepository.save(event);
        return mapAuditToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> getAuditEvents(int page, int size) {
        String tenantId = TenantContext.getRequiredTenantId();
        return auditRepository
            .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
            .map(this::mapAuditToResponse);
    }

    @Transactional
    public void recordUsage(RecordUsageRequest req) {
        String tenantId = TenantContext.getRequiredTenantId();
        UsageRecord record = new UsageRecord(
            tenantId,
            req.service(),
            req.operation(),
            req.quantity(),
            req.unit() != null ? req.unit() : "TOKENS",
            req.costEstimate()
        );
        usageRepository.save(record);
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse getUsageSummary() {
        String tenantId = TenantContext.getRequiredTenantId();
        List<Object[]> rows = usageRepository.sumQuantityByService(tenantId);
        Map<String, Long> summary = new HashMap<>();
        for (Object[] r : rows) {
            String service = (String) r[0];
            Long total = ((Number) r[1]).longValue();
            summary.put(service, total);
        }
        List<UsageRecord> allRecords = usageRepository.findByTenantId(tenantId);
        return new UsageSummaryResponse(tenantId, summary, allRecords.size());
    }

    @Transactional(readOnly = true)
    public QuotaResponse getTenantQuota() {
        String tenantId = TenantContext.getRequiredTenantId();
        TenantQuota q = quotaRepository.findByTenantId(tenantId)
            .orElseGet(() -> new TenantQuota(tenantId, 10000, 50000, 5120));
        return new QuotaResponse(q.getId(), q.getTenantId(), q.getMonthlyTicketLimit(), q.getMonthlyAiCallLimit(), q.getStorageLimitMb(), q.getUpdatedAt());
    }

    private AuditEventResponse mapAuditToResponse(AuditEvent a) {
        return new AuditEventResponse(
            a.getId(),
            a.getTenantId(),
            a.getEventType(),
            a.getActorId(),
            a.getResourceType(),
            a.getResourceId(),
            a.getPayload(),
            a.getTraceId(),
            a.getCreatedAt()
        );
    }
}
