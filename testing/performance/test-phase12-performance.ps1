# ==============================================================================
# Phase 12 Performance, Experiments & Disaster Recovery Verification Suite
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Phase 12: Validating Performance, Experiments & Disaster Recovery" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$passed = 0
$total = 0

function Assert-Test($condition, $message) {
    $script:total++
    if ($condition) {
        Write-Host "[PASS] $message" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "[FAIL] $message" -ForegroundColor Red
        exit 1
    }
}

# 1. Validate k6 Load Testing Scripts
Write-Host "`n1. Validating k6 Load Test Scripts..." -ForegroundColor Yellow
$k6Scripts = @(
    "testing/load/ticket-creation-load.js",
    "testing/load/hybrid-rag-load.js"
)

foreach ($s in $k6Scripts) {
    Assert-Test (Test-Path $s) "Load test script exists: $s"
    $content = Get-Content -Raw $s
    Assert-Test ($content.Contains("export const options") -and $content.Contains("thresholds")) "Script $s configures k6 stages and latency thresholds"
}

# 2. Test Synthetic Benchmark Data Generator
Write-Host "`n2. Executing Synthetic Benchmark Generator..." -ForegroundColor Yellow
$benchmarkOutput = "testing/benchmark-tickets-test.json"
& powershell -File scripts/generate-benchmark-data.ps1 -Count 50 -OutputPath $benchmarkOutput | Out-Null
Assert-Test (Test-Path $benchmarkOutput) "Synthetic generator produced benchmark output file"

$tickets = Get-Content -Encoding UTF8 -Raw $benchmarkOutput | ConvertFrom-Json
Assert-Test ($tickets.Count -eq 50) "Generated exactly 50 benchmark tickets"
Assert-Test ($tickets[0].tenantId -ne $null -and $tickets[0].priority -ne $null) "Synthetic tickets contain valid multi-tenant schema fields"
Remove-Item -Path $benchmarkOutput -Force

# 3. Validate Query Plans Documentation
Write-Host "`n3. Validating Query Plan & Indexing Documentation..." -ForegroundColor Yellow
$qpDoc = Get-Content -Encoding UTF8 -Raw "docs/performance/query-plans.md"
Assert-Test ($qpDoc.Contains("EXPLAIN (ANALYZE, BUFFERS)")) "Query plans document includes EXPLAIN ANALYZE"
Assert-Test ($qpDoc.Contains("idx_tickets_tenant_status_created")) "Composite index execution plan documented"
Assert-Test ($qpDoc.Contains("idx_knowledge_docs_fts_gin")) "GIN full-text search index execution plan documented"
Assert-Test ($qpDoc.Contains("100,000 / day")) "High-throughput capacity modeling table documented"

# 4. Validate Disaster Recovery & Backup Procedures
Write-Host "`n4. Validating Disaster Recovery & Backup Script..." -ForegroundColor Yellow
$drDoc = Get-Content -Encoding UTF8 -Raw "docs/operations/disaster-recovery.md"
Assert-Test ($drDoc.Contains("Recovery Point Objective (RPO)") -and $drDoc.Contains("< 1 minute")) "RPO target < 1 minute documented"
Assert-Test ($drDoc.Contains("Recovery Time Objective (RTO)") -and $drDoc.Contains("< 15 minutes")) "RTO target < 15 minutes documented"

$backupScript = Get-Content -Raw "scripts/backup-restore.ps1"
Assert-Test ($backupScript.Contains("pg_dump") -and $backupScript.Contains("identity_db") -and $backupScript.Contains("ticket_db")) "Backup script covers all logical databases"

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "Phase 12 Verification COMPLETE: $passed / $total Passed!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
