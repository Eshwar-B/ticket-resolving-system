# =============================================================================
# test-ticket-isolation.ps1
# Phase 3 — Ticket Service Tenant Isolation Tests
# Tests ticket schema RLS, state machine constraints, and cross-tenant access denial
# =============================================================================

$ErrorActionPreference = "Continue"
$results = @{}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  Phase 3 Ticket Service Isolation Tests" -ForegroundColor Cyan
Write-Host "=========================================`n" -ForegroundColor Cyan

function Test-Result {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $status = if ($Passed) { "PASS" } else { "FAIL" }
    $color = if ($Passed) { "Green" } else { "Red" }
    $results[$Name] = $Passed
    Write-Host "  $($status.PadRight(6)) $Name" -ForegroundColor $color
    if ($Detail) { Write-Host "         $Detail" -ForegroundColor Gray }
}

function Invoke-Psql {
    param([string]$User, [string]$Db, [string]$Sql)
    $output = docker exec enterprise-ai-postgres psql -U $User -d $Db -t -A -c $Sql 2>&1
    return $output
}

# ---------------------------------------------------------------------------
# Test 1: Tenant Alpha ticket isolation
# ---------------------------------------------------------------------------
Write-Host "[ Ticket RLS Tests ]" -ForegroundColor Yellow

$alphaTickets = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-alpha'; SELECT COUNT(*) FROM tickets; COMMIT;"
$alphaCount = ($alphaTickets | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ($alphaCount -eq "1")
Test-Result "RLS_Alpha_Ticket_Count" $pass "tenant-alpha sees $alphaCount ticket(s) (expected 1)"

# ---------------------------------------------------------------------------
# Test 2: Tenant Beta ticket isolation
# ---------------------------------------------------------------------------
$betaTickets = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-beta'; SELECT COUNT(*) FROM tickets; COMMIT;"
$betaCount = ($betaTickets | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ($betaCount -eq "1")
Test-Result "RLS_Beta_Ticket_Count" $pass "tenant-beta sees $betaCount ticket(s) (expected 1)"

# ---------------------------------------------------------------------------
# Test 3: Cross-tenant access denial — alpha cannot see beta tickets
# ---------------------------------------------------------------------------
$betaId = "d1000000-0000-0000-0000-000000000001"
$crossAccess = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-alpha'; SELECT COUNT(*) FROM tickets WHERE id = '$betaId'; COMMIT;"
$count = ($crossAccess | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ($count -eq "0")
Test-Result "RLS_Cross_Tenant_Denial" $pass "Alpha querying beta ticket id returns $count row(s) (expected 0)"

# ---------------------------------------------------------------------------
# Test 4: Default deny — no tenant set
# ---------------------------------------------------------------------------
$noTenant = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "SELECT COUNT(*) FROM tickets;"
$noTenantCount = ($noTenant | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ($noTenantCount -eq "0")
Test-Result "RLS_Default_Deny_Tickets" $pass "No tenant set: $noTenantCount rows returned (expected 0)"

# ---------------------------------------------------------------------------
# Test 5: Customer RLS isolation
# ---------------------------------------------------------------------------
$alphaCustomers = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-alpha'; SELECT COUNT(*) FROM customers; COMMIT;"
$custCount = ($alphaCustomers | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ([int]$custCount -ge 2)
Test-Result "RLS_Customer_Alpha_Isolation" $pass "tenant-alpha sees $custCount customer(s) (expected >=2)"

# ---------------------------------------------------------------------------
# Test 6: Seed data integrity
# ---------------------------------------------------------------------------
$seedCheck = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-alpha'; SELECT idempotency_key FROM tickets WHERE idempotency_key = 'seed-ticket-alpha-001'; COMMIT;"
$pass = [bool]($seedCheck -match "seed-ticket-alpha-001")
Test-Result "Seed_Data_Integrity" $pass "Seed ticket idempotency_key present"

# ---------------------------------------------------------------------------
# Test 7: State machine enum integrity
# ---------------------------------------------------------------------------
Write-Host "`n[ Schema & Enum Tests ]" -ForegroundColor Yellow

$statusEnum = Invoke-Psql -User "postgres" -Db "ticket_db" -Sql "SELECT enumlabel FROM pg_enum JOIN pg_type ON pg_enum.enumtypid = pg_type.oid WHERE typname = 'ticket_status' ORDER BY enumsortorder;"
$expectedStatuses = @("NEW", "OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "WAITING_FOR_APPROVAL", "RESOLVED", "CLOSED", "CANCELLED")
$allFound = $true
$statusStr = ($statusEnum | Out-String)
foreach ($s in $expectedStatuses) {
    if ($statusStr -notmatch $s) { $allFound = $false }
}
Test-Result "Schema_Status_Enum" $allFound "ticket_status enum has all 8 valid states"

# ---------------------------------------------------------------------------
# Test 8: RLS enabled on all 6 tables
# ---------------------------------------------------------------------------
$rlsTables = Invoke-Psql -User "postgres" -Db "ticket_db" -Sql "SELECT COUNT(*) FROM pg_tables WHERE schemaname='public' AND rowsecurity=true AND tablename IN ('tickets','customers','ticket_comments','ticket_assignments','ticket_history','ticket_sla');"
$rlsCount = ($rlsTables | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ($rlsCount -eq "6")
Test-Result "Schema_RLS_All_Tables" $pass "$rlsCount/6 ticket tables have RLS enabled"

# ---------------------------------------------------------------------------
# Test 9: Indexes exist
# ---------------------------------------------------------------------------
$indexCheck = Invoke-Psql -User "postgres" -Db "ticket_db" -Sql "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' AND tablename='tickets';"
$idxCount = ($indexCheck | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
$pass = ([int]$idxCount -ge 7)
Test-Result "Schema_Ticket_Indexes" $pass "$idxCount indexes on tickets table (expected >=7)"

# ---------------------------------------------------------------------------
# Test 10: app_user grants
# ---------------------------------------------------------------------------
Write-Host "`n[ Permissions Tests ]" -ForegroundColor Yellow
$grantTest = Invoke-Psql -User "app_user" -Db "ticket_db" -Sql "BEGIN; SET LOCAL app.tenant_id = 'tenant-alpha'; SELECT id FROM tickets LIMIT 1; COMMIT;"
$pass = ($grantTest -notmatch "ERROR" -and $grantTest -notmatch "permission denied")
Test-Result "Permissions_App_User_Select" $pass "app_user can SELECT from tickets (RLS-scoped)"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
$total = $results.Count
$passed = ($results.Values | Where-Object { $_ -eq $true }).Count
$failed = $total - $passed

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  Results: $passed/$total PASSED" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "=========================================`n" -ForegroundColor Cyan

if ($failed -gt 0) {
    Write-Host "FAILED tests:" -ForegroundColor Red
    $results.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object {
        Write-Host "  - $($_.Key)" -ForegroundColor Red
    }
    exit 1
} else {
    Write-Host "All tests PASSED. Phase 3 ticket schema verified." -ForegroundColor Green
    exit 0
}
