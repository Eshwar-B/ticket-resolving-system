package com.enterpriseai.audit.web;

import com.enterpriseai.audit.dto.*;
import com.enterpriseai.audit.service.UsageAuditService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UsageAuditController {

    private final UsageAuditService usageAuditService;

    public UsageAuditController(UsageAuditService usageAuditService) {
        this.usageAuditService = usageAuditService;
    }

    @PostMapping("/audit")
    public ResponseEntity<AuditEventResponse> recordAuditEvent(
            @Valid @RequestBody RecordAuditEventRequest req) {
        AuditEventResponse resp = usageAuditService.recordAuditEvent(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditEventResponse>> getAuditEvents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(usageAuditService.getAuditEvents(page, size));
    }

    @PostMapping("/usage")
    public ResponseEntity<Void> recordUsage(@Valid @RequestBody RecordUsageRequest req) {
        usageAuditService.recordUsage(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/usage")
    public ResponseEntity<UsageSummaryResponse> getUsageSummary() {
        return ResponseEntity.ok(usageAuditService.getUsageSummary());
    }

    @GetMapping("/quotas")
    public ResponseEntity<QuotaResponse> getTenantQuota() {
        return ResponseEntity.ok(usageAuditService.getTenantQuota());
    }
}
