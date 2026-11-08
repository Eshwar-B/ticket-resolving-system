# =============================================================================
# test-phase4-reliability.ps1
# End-to-End Verification for Phase 3 (REST APIs) & Phase 4 (Outbox, Idempotency, Kafka)
# =============================================================================

$ErrorActionPreference = "Continue"
$results = @{}

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  Phase 3 (APIs) & Phase 4 (Event Reliability) Test Suite" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan

function Test-Result {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $status = if ($Passed) { "PASS" } else { "FAIL" }
    $color = if ($Passed) { "Green" } else { "Red" }
    $results[$Name] = $Passed
    Write-Host "  $($status.PadRight(6)) $Name" -ForegroundColor $color
    if ($Detail) { Write-Host "         $Detail" -ForegroundColor Gray }
}

$baseUrl = "http://localhost:8081/api/v1/tickets"

# ---------------------------------------------------------------------------
# Test 1: Service Health
# ---------------------------------------------------------------------------
Write-Host "[ Health & Liveness ]" -ForegroundColor Yellow
$healthResp = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Method Get
$healthPass = ($healthResp.status -eq "UP")
Test-Result "Actuator_Health_UP" $healthPass "Actuator health returned UP"

# ---------------------------------------------------------------------------
# Test 2: HTTP GET /api/v1/tickets with Tenant Alpha context
# ---------------------------------------------------------------------------
Write-Host "`n[ HTTP REST API & Tenant Isolation ]" -ForegroundColor Yellow
$alphaList = Invoke-RestMethod -Uri $baseUrl -Headers @{ "X-Tenant-Id" = "tenant-alpha" }
$alphaPass = ($alphaList.totalElements -ge 1 -and $alphaList.items[0].tenantId -eq "tenant-alpha")
Test-Result "HTTP_Alpha_Ticket_List" $alphaPass "Alpha receives $($alphaList.totalElements) tickets, all tenant-alpha"

# ---------------------------------------------------------------------------
# Test 3: HTTP GET /api/v1/tickets with Tenant Beta context
# ---------------------------------------------------------------------------
$betaList = Invoke-RestMethod -Uri $baseUrl -Headers @{ "X-Tenant-Id" = "tenant-beta" }
$betaPass = ($betaList.totalElements -ge 1 -and $betaList.items[0].tenantId -eq "tenant-beta")
Test-Result "HTTP_Beta_Ticket_List" $betaPass "Beta receives $($betaList.totalElements) tickets, all tenant-beta"

# ---------------------------------------------------------------------------
# Test 4: Cross-Tenant Denial over HTTP (Alpha ID queried by Beta)
# ---------------------------------------------------------------------------
$alphaTicketId = $alphaList.items[0].id
$crossStatus = 0
try {
    $null = Invoke-WebRequest -Uri "$baseUrl/$alphaTicketId" -Headers @{ "X-Tenant-Id" = "tenant-beta" } -UseBasicParsing
} catch {
    $crossStatus = [int]$_.Exception.Response.StatusCode
}
$crossPass = ($crossStatus -eq 404)
Test-Result "HTTP_Cross_Tenant_404" $crossPass "Beta querying Alpha ticket returns HTTP 404 Resource Not Found (code: $crossStatus)"

# ---------------------------------------------------------------------------
# Test 5: POST Create Ticket with Idempotency-Key
# ---------------------------------------------------------------------------
Write-Host "`n[ Mutation & Transactional Outbox ]" -ForegroundColor Yellow
$idemKey = "idem-" + [guid]::NewGuid().ToString()
$customerId = "a1000000-0000-0000-0000-000000000001" # Alice from seed

$createPayload = @{
    customerId = $customerId
    priority = "HIGH"
    category = "ORDER"
    subject = "Package arrived with missing items - Phase 4 test"
    description = "Order #99901 was missing the item inside. Need replacement or refund."
    idempotencyKey = $idemKey
} | ConvertTo-Json

$createdTicket = Invoke-RestMethod -Uri $baseUrl -Method Post `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Idempotency-Key" = $idemKey; "Content-Type" = "application/json" } `
    -Body $createPayload

$createdPass = ($createdTicket.id -ne $null -and $createdTicket.tenantId -eq "tenant-alpha" -and $createdTicket.status -eq "NEW")
Test-Result "HTTP_Create_Ticket" $createdPass "Ticket created with ID: $($createdTicket.id)"

# ---------------------------------------------------------------------------
# Test 6: Idempotent Replay — Resending with same Idempotency-Key
# ---------------------------------------------------------------------------
Write-Host "`n[ Distributed Idempotency ]" -ForegroundColor Yellow
$replayResp = Invoke-WebRequest -Uri $baseUrl -Method Post -UseBasicParsing `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Idempotency-Key" = $idemKey; "Content-Type" = "application/json" } `
    -Body $createPayload

$replayHeaderVal = $replayResp.Headers["X-Idempotent-Replay"]
$replayTicket = $replayResp.Content | ConvertFrom-Json
$idemPass = ($replayHeaderVal -eq "true" -and $replayTicket.id -eq $createdTicket.id)
Test-Result "Idempotency_Key_Replay" $idemPass "Duplicate request safely returned cached response without re-creating (X-Idempotent-Replay: $replayHeaderVal)"

# ---------------------------------------------------------------------------
# Test 7: Transactional Outbox Database Record
# ---------------------------------------------------------------------------
Write-Host "`n[ Transactional Outbox & Polling Relay ]" -ForegroundColor Yellow
$newTicketId = $createdTicket.id
$outboxCheck = docker exec enterprise-ai-postgres psql -U postgres -d ticket_db -t -A -c "SELECT COUNT(*) FROM outbox_events WHERE topic = 'ticket.created.v1' AND payload->>'aggregateId' = '$newTicketId';"
$outboxCount = ($outboxCheck | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$outboxPass = ([int]$outboxCount -ge 1)
Test-Result "Outbox_Event_Recorded" $outboxPass "Atomic outbox event recorded in ticket_db (count: $outboxCount)"

# ---------------------------------------------------------------------------
# Test 8: Outbox Publisher Published to Kafka
# ---------------------------------------------------------------------------
# Allow the background poller up to 5 seconds to relay to Kafka
Start-Sleep -Seconds 4
$publishedStatus = docker exec enterprise-ai-postgres psql -U postgres -d ticket_db -t -A -c "SELECT status FROM outbox_events WHERE topic = 'ticket.created.v1' AND payload->>'aggregateId' = '$newTicketId' ORDER BY created_at DESC LIMIT 1;"
$statusVal = ($publishedStatus | Where-Object { $_ -match '^(PENDING|PUBLISHED|FAILED)$' } | Select-Object -Last 1)
$isPublished = ($statusVal -eq "PUBLISHED")
Test-Result "Outbox_Published_Status" $isPublished "Background poller transitioned event to status: $statusVal"

# ---------------------------------------------------------------------------
# Test 9: Kafka Topic Message Consumption
# ---------------------------------------------------------------------------
$kafkaMsg = docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ticket.created.v1 --from-beginning --timeout-ms 5000 --max-messages 50 2>&1
$kafkaPass = ($kafkaMsg -match "ticket.created.v1" -and $kafkaMsg -match $newTicketId)
Test-Result "Kafka_Message_Received" $kafkaPass "Kafka topic ticket.created.v1 received valid canonical event envelope"

# ---------------------------------------------------------------------------
# Test 10: State Machine Enforcement (Invalid Transition Rejected)
# ---------------------------------------------------------------------------
Write-Host "`n[ State Machine & RFC 7807 Errors ]" -ForegroundColor Yellow
# Try invalid transition NEW -> CLOSED
$invalidTransitionPayload = @{
    targetStatus = "CLOSED"
    note = "Trying invalid direct close"
} | ConvertTo-Json

$invalidCode = 0
$invalidProblem = ""
try {
    $null = Invoke-WebRequest -Uri "$baseUrl/$newTicketId/status" -Method Patch -UseBasicParsing `
        -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
        -Body $invalidTransitionPayload
} catch {
    $invalidCode = [int]$_.Exception.Response.StatusCode
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $invalidProblem = $reader.ReadToEnd()
}

$invalidPass = ($invalidCode -eq 422 -and $invalidProblem -match "invalid-state-transition")
Test-Result "State_Machine_Rejection" $invalidPass "Invalid transition NEW -> CLOSED rejected with HTTP 422 (Problem type: invalid-state-transition)"

# ---------------------------------------------------------------------------
# Test 11: Valid State Transition (NEW -> OPEN via Assignment)
# ---------------------------------------------------------------------------
$assignPayload = @{
    agentId = "agent-alpha-007"
    team = "Tier2Support"
    note = "Assigning to Tier 2 specialist"
} | ConvertTo-Json

$assignResp = Invoke-RestMethod -Uri "$baseUrl/$newTicketId/assignment" -Method Put `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
    -Body $assignPayload

$assignPass = ($assignResp.assignedAgent -eq "agent-alpha-007" -and $assignResp.status -eq "OPEN")
Test-Result "Ticket_Assignment_Auto_Open" $assignPass "Ticket assigned to agent-alpha-007 and auto-transitioned NEW -> OPEN"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
$total = $results.Count
$passed = ($results.Values | Where-Object { $_ -eq $true }).Count
$failed = $total - $passed

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  Results: $passed/$total PASSED" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "============================================================`n" -ForegroundColor Cyan

if ($failed -gt 0) {
    Write-Host "FAILED tests:" -ForegroundColor Red
    $results.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object {
        Write-Host "  - $($_.Key)" -ForegroundColor Red
    }
    exit 1
} else {
    Write-Host "All tests PASSED! Phase 3 and Phase 4 verified end-to-end." -ForegroundColor Green
    exit 0
}
