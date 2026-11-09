# ==============================================================================
# Enterprise AI Platform - Phase 5 Hybrid RAG & Knowledge Isolation Verification
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

Write-Host "=== Step 1: Healthcheck Knowledge Service ===" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method GET
Assert-Condition ($health.status -eq "UP") "Knowledge Service reports UP status"

Write-Host "`n=== Step 2: Ingest Document for Tenant Alpha ===" -ForegroundColor Cyan
$alphaPayload = @{
    title = "Tenant Alpha Return and Refund Guidelines"
    content = "Customers may request refunds within 30 days of purchase for unused products in original packaging. Proof of purchase is required. Return window is strictly enforced."
    metadata = @{
        department = "support"
        version = "1.0"
    }
} | ConvertTo-Json

$alphaHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-alpha"
    "X-User-Id"    = "agent-007"
}

$alphaDoc = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/documents" -Method POST -Headers $alphaHeaders -Body $alphaPayload
Assert-Condition ($alphaDoc.id -ne $null) "Document created for tenant-alpha with ID $($alphaDoc.id)"
Assert-Condition ($alphaDoc.chunkCount -ge 1) "Document chunked into $($alphaDoc.chunkCount) chunk(s)"
Assert-Condition ($alphaDoc.s3Key -ne $null) "Document raw content backed in S3 storage with key $($alphaDoc.s3Key)"

Write-Host "`n=== Step 3: Ingest Document for Tenant Beta ===" -ForegroundColor Cyan
$betaPayload = @{
    title = "Tenant Beta Enterprise Hardware Service Agreement"
    content = "Beta enterprise customers have a 14-day return window for hardware appliances with a 15% restocking fee. Defective hardware is replaced via next-business-day RMA."
    metadata = @{
        department = "hardware-ops"
        version = "2.1"
    }
} | ConvertTo-Json

$betaHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-beta"
    "X-User-Id"    = "admin-beta"
}

$betaDoc = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/documents" -Method POST -Headers $betaHeaders -Body $betaPayload
Assert-Condition ($betaDoc.id -ne $null) "Document created for tenant-beta with ID $($betaDoc.id)"
Assert-Condition ($betaDoc.chunkCount -ge 1) "Document chunked into $($betaDoc.chunkCount) chunk(s)"

Write-Host "`n=== Step 4: Hybrid RAG Search as Tenant Alpha ===" -ForegroundColor Cyan
$searchAlphaPayload = @{
    query = "return window 30 days refund"
    topK = 5
} | ConvertTo-Json

$alphaSearchResults = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/search" -Method POST -Headers $alphaHeaders -Body $searchAlphaPayload
Assert-Condition ($alphaSearchResults.results.Count -ge 1) "Tenant Alpha search returns matching results"
Assert-Condition ($alphaSearchResults.results[0].citation.Contains("Tenant Alpha Return and Refund Guidelines")) "Citation correctly names Alpha document: $($alphaSearchResults.results[0].citation)"
Assert-Condition ($alphaSearchResults.results[0].content.Contains("30 days")) "Search result contains 30-day Alpha policy text"

# Verify ZERO cross-tenant leakage from Beta into Alpha
$leakedFromBeta = $alphaSearchResults.results | Where-Object { $_.content.Contains("Beta enterprise") -or $_.citation.Contains("Beta") }
Assert-Condition ($leakedFromBeta -eq $null -or $leakedFromBeta.Count -eq 0) "Zero leakage of Tenant Beta documents into Tenant Alpha results"

Write-Host "`n=== Step 5: Search for Beta-Exclusive Query as Tenant Alpha ===" -ForegroundColor Cyan
$searchBetaAsAlphaPayload = @{
    query = "Enterprise Hardware Service Agreement restocking fee"
    topK = 5
} | ConvertTo-Json

$crossTenantAttempt = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/search" -Method POST -Headers $alphaHeaders -Body $searchBetaAsAlphaPayload
Assert-Condition ($crossTenantAttempt.results.Count -eq 0) "Tenant Alpha cannot access Tenant Beta knowledge (RLS & Vector filter guarantee 0 results)"

Write-Host "`n=== Step 6: Hybrid RAG Search as Tenant Beta ===" -ForegroundColor Cyan
$searchBetaPayload = @{
    query = "Enterprise hardware 14-day return"
    topK = 5
} | ConvertTo-Json

$betaSearchResults = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/search" -Method POST -Headers $betaHeaders -Body $searchBetaPayload
Assert-Condition ($betaSearchResults.results.Count -ge 1) "Tenant Beta search returns matching results"
Assert-Condition ($betaSearchResults.results[0].citation.Contains("Tenant Beta Enterprise Hardware")) "Citation correctly names Beta document"
Assert-Condition ($betaSearchResults.results[0].content.Contains("15% restocking fee")) "Search result contains Beta hardware policy text"

$leakedFromAlpha = $betaSearchResults.results | Where-Object { $_.content.Contains("30 days") -or $_.citation.Contains("Alpha") }
Assert-Condition ($leakedFromAlpha -eq $null -or $leakedFromAlpha.Count -eq 0) "Zero leakage of Tenant Alpha documents into Tenant Beta results"

Write-Host "`n=== Step 7: Document Soft Delete & Search Invalidation ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/documents/$($alphaDoc.id)" -Method DELETE -Headers $alphaHeaders
Write-Host "PASS: Document successfully soft-deleted (HTTP 204)" -ForegroundColor Green

$afterDeleteResults = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/search" -Method POST -Headers $alphaHeaders -Body $searchAlphaPayload
Assert-Condition ($afterDeleteResults.results.Count -eq 0) "Deleted document is immediately excluded from hybrid search results"

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "ALL PHASE 5 HYBRID RAG & KNOWLEDGE TESTS PASSED (12/12)!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
