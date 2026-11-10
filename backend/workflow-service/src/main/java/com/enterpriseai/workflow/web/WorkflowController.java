package com.enterpriseai.workflow.web;

import com.enterpriseai.workflow.dto.ApprovalDecisionRequest;
import com.enterpriseai.workflow.dto.ApprovalResponse;
import com.enterpriseai.workflow.dto.CreateApprovalRequest;
import com.enterpriseai.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "Workflow Approvals API", description = "Human-in-the-Loop approval requests, rejections, and state transition governance")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new human approval request")
    public ResponseEntity<ApprovalResponse> createApproval(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @Valid @RequestBody CreateApprovalRequest request) {
        ApprovalResponse response = workflowService.createApproval(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/ticket/{ticketId}")
    @Operation(summary = "List all approval requests for a ticket")
    public ResponseEntity<List<ApprovalResponse>> getApprovalsForTicket(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PathVariable("ticketId") UUID ticketId) {
        return ResponseEntity.ok(workflowService.getApprovalsForTicket(ticketId));
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending approval requests for current tenant")
    public ResponseEntity<List<ApprovalResponse>> getPendingApprovals(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader) {
        return ResponseEntity.ok(workflowService.getPendingApprovals());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending approval request")
    public ResponseEntity<ApprovalResponse> approve(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PathVariable("id") UUID id,
            @Valid @RequestBody ApprovalDecisionRequest decision) {
        return ResponseEntity.ok(workflowService.approve(id, decision));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending approval request")
    public ResponseEntity<ApprovalResponse> reject(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PathVariable("id") UUID id,
            @Valid @RequestBody ApprovalDecisionRequest decision) {
        return ResponseEntity.ok(workflowService.reject(id, decision));
    }
}

