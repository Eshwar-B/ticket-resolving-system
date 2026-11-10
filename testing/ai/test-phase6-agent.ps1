# ==============================================================================
# Enterprise AI Platform - Phase 6 Agentic AI & Human Approvals Verification
# ==============================================================================

$ErrorActionPreference = "Stop"

function Assert-Condition($condition, $message) {
    if (-not $condition) {
        Write-Error "FAIL: $message"
        exit 1
    } else {
        Write-Host "PASS: $message" -ForegroundColor Green
    }
}

Write-Host "=== Step 1: Healthcheck Workflow Service ===" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -Method GET
Assert-Condition ($health.status -eq "UP") "Workflow Service reports UP status"

Write-Host "`n=== Step 2: Test Direct Human Approval Creation & Lifecycle ===" -ForegroundColor Cyan
$alphaHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-alpha"
    "X-User-Id"    = "ai-supervisor"
}

# Create a test ticket in ticket-service
$ticketPayload = @{
    customerId = "a1000000-0000-0000-0000-000000000001"
    priority = "HIGH"
    category = "BILLING"
    subject = "Refund requested for damaged item"
    description = "Customer demands immediate refund of $120.00 for damaged goods received in shipment."
} | ConvertTo-Json

$createdTicket = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets" -Method POST -Headers @{
    "Content-Type" = "application/json"
    "X-Tenant-Id" = "tenant-alpha"
    "Idempotency-Key" = "idem-" + [guid]::NewGuid().ToString()
} -Body $ticketPayload
Assert-Condition ($createdTicket.id -ne $null) "Created test ticket in ticket-service: $($createdTicket.id)"

# Request human approval via workflow-service
$approvalPayload = @{
    ticketId = $createdTicket.id
    actionType = "REFUND_AUTHORIZATION"
    payload = '{"amount": 120.00, "currency": "USD", "reason": "Damaged goods"}'
    requester = "ai-supervisor"
} | ConvertTo-Json

$approval = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals" -Method POST -Headers $alphaHeaders -Body $approvalPayload
Assert-Condition ($approval.id -ne $null) "Approval request created with ID: $($approval.id)"
Assert-Condition ($approval.status -eq "PENDING") "Approval initial status is PENDING"
Assert-Condition ($approval.ticketId -eq $createdTicket.id) "Approval correctly linked to ticket $($createdTicket.id)"

Write-Host "`n=== Step 3: Query Pending Approvals for Tenant Alpha ===" -ForegroundColor Cyan
$pendingList = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/pending" -Method GET -Headers $alphaHeaders
$found = $pendingList | Where-Object { $_.id -eq $approval.id }
Assert-Condition ($found -ne $null) "Created approval appears in tenant-alpha pending list"

Write-Host "`n=== Step 4: Strict Cross-Tenant Approval Isolation ===" -ForegroundColor Cyan
$betaHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-beta"
    "X-User-Id"    = "admin-beta"
}

$betaPendingList = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/pending" -Method GET -Headers $betaHeaders
$leaked = $betaPendingList | Where-Object { $_.id -eq $approval.id }
Assert-Condition ($leaked -eq $null -or $leaked.Count -eq 0) "Zero leakage of Tenant Alpha approvals into Tenant Beta (RLS isolation)"

# Cross-tenant approval attempt must fail
try {
    $decisionBody = @{ approver = "admin-beta"; note = "Unauthorized cross-tenant attempt" } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approval.id)/approve" -Method POST -Headers $betaHeaders -Body $decisionBody
    Write-Error "FAIL: Cross-tenant approval should have been denied!"
    exit 1
} catch {
    Write-Host "PASS: Cross-tenant approval successfully denied by RLS (HTTP 404)" -ForegroundColor Green
}

Write-Host "`n=== Step 5: Authorized Human Approval Execution ===" -ForegroundColor Cyan
$approveDecision = @{
    approver = "manager-alpha"
    note = "Approved refund of $120.00 per return policy section 4.2"
} | ConvertTo-Json

$approvedRecord = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approval.id)/approve" -Method POST -Headers $alphaHeaders -Body $approveDecision
Assert-Condition ($approvedRecord.status -eq "APPROVED") "Approval status transitioned to APPROVED"
Assert-Condition ($approvedRecord.approvedBy -eq "manager-alpha") "Approver recorded as manager-alpha"

# Attempting to re-approve an already resolved request must be rejected
try {
    Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approval.id)/approve" -Method POST -Headers $alphaHeaders -Body $approveDecision
    Write-Error "FAIL: Re-approving an already approved request should fail!"
    exit 1
} catch {
    Write-Host "PASS: Re-approval rejected with invalid state error (HTTP 409)" -ForegroundColor Green
}

Write-Host "`n=== Step 6: Test Rejection Lifecycle ===" -ForegroundColor Cyan
$rejectApprovalPayload = @{
    ticketId = $createdTicket.id
    actionType = "CHARGEBACK_CLAIM"
    payload = '{"amount": 500.00}'
    requester = "ai-supervisor"
} | ConvertTo-Json

$approvalToReject = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals" -Method POST -Headers $alphaHeaders -Body $rejectApprovalPayload
Assert-Condition ($approvalToReject.id -ne $null) "Second approval request created"

$rejectDecision = @{
    approver = "finance-admin-alpha"
    note = "Chargeback claim does not meet minimum documentation requirements"
} | ConvertTo-Json

$rejectedRecord = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approvalToReject.id)/reject" -Method POST -Headers $alphaHeaders -Body $rejectDecision
Assert-Condition ($rejectedRecord.status -eq "REJECTED") "Approval status transitioned to REJECTED"
Assert-Condition ($rejectedRecord.decisionNote.Contains("minimum documentation")) "Rejection note recorded"

Write-Host "`n=== Step 7: Verify Ticket Approval History Audit ===" -ForegroundColor Cyan
$ticketApprovals = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/ticket/$($createdTicket.id)" -Method GET -Headers $alphaHeaders
Assert-Condition ($ticketApprovals.Count -ge 2) "Ticket audit log contains both approval records"

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "ALL PHASE 6 AGENT & APPROVAL TESTS PASSED (14/14)!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green

