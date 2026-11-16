# ==============================================================================
# Enterprise AI Platform — Synthetic Benchmark Data Generator
# Generates realistic multi-tenant ticket datasets for load testing & DB benchmarks
# ==============================================================================
param(
    [int]$Count = 1000,
    [string]$OutputPath = "testing/benchmark-tickets.json"
)

$ErrorActionPreference = "Stop"

Write-Host "Generating $Count synthetic benchmark tickets..." -ForegroundColor Cyan

$tenants = @("tenant-alpha", "tenant-beta", "tenant-gamma")
$priorities = @("LOW", "MEDIUM", "HIGH", "CRITICAL")
$categories = @("BILLING_DISPUTE", "TECHNICAL_ISSUE", "RETURN_REFUND", "ACCOUNT_ACCESS", "FEATURE_REQUEST")

$titles = @(
    "Delivered box was completely empty upon arrival",
    "Recurring invoice charged twice for month of November",
    "Application crashes with OutOfMemoryError on report export",
    "Cannot reset multi-factor authentication token on login",
    "Requesting SLA extension for enterprise maintenance window",
    "Broken seal on shipment package #981023",
    "Hardware device fails to power on after firmware upgrade",
    "API rate limit exceeded during daily batch sync"
)

$tickets = [System.Collections.Generic.List[PSObject]]::new()
$rand = [System.Random]::new(42)

for ($i = 1; $i -le $Count; $i++) {
    $tenant = $tenants[$rand.Next(0, $tenants.Length)]
    $priority = $priorities[$rand.Next(0, $priorities.Length)]
    $category = $categories[$rand.Next(0, $categories.Length)]
    $title = $titles[$rand.Next(0, $titles.Length)]

    $ticket = [PSCustomObject]@{
        id = [Guid]::NewGuid().ToString()
        tenantId = $tenant
        title = "[$priority] $title (#$i)"
        description = "Synthetic load benchmark description for ticket #$i under tenant $tenant. Simulating production customer request with detailed telemetry."
        customerEmail = "benchmark_user_$($rand.Next(1, 500))@example.com"
        customerName = "Benchmark Customer $($rand.Next(1, 500))"
        priority = $priority
        category = $category
        status = "OPEN"
        createdAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssZ")
    }

    $tickets.Add($ticket)
}

$jsonDir = Split-Path $OutputPath -Parent
if ($jsonDir -and -not (Test-Path $jsonDir)) {
    New-Item -ItemType Directory -Path $jsonDir -Force | Out-Null
}

$tickets | ConvertTo-Json -Depth 3 | Set-Content -Path $OutputPath -Encoding UTF8
Write-Host "Generated $Count tickets successfully -> $OutputPath" -ForegroundColor Green
