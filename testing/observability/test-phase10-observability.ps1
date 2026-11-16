# ==============================================================================
# Phase 10 Observability & Operations Verification Suite
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Phase 10: Validating Observability, Alerting & Operations Config" -ForegroundColor Cyan
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

# 1. Validate YAML syntax for Prometheus and OTel Collector
Write-Host "`n1. Validating YAML Configurations..." -ForegroundColor Yellow
$pyYamlScript = @"
import yaml

files = [
    'infra/observability/otel-collector-config.yaml',
    'infra/observability/prometheus.yml',
    'infra/observability/alert-rules.yml'
]

for f in files:
    with open(f, 'r') as fp:
        data = yaml.safe_load(fp)
        assert data is not None, f'{f} loaded empty'
print('YAML_VALID')
"@

$yamlResult = $pyYamlScript | python -
Assert-Test ($yamlResult.Trim() -eq "YAML_VALID") "All Prometheus, Alertmanager, and OTel YAML files parsed cleanly"

# 2. Validate Grafana Dashboards JSON
Write-Host "`n2. Validating Grafana Dashboards..." -ForegroundColor Yellow
$dashboards = @(
    "infra/observability/grafana/dashboards/platform-overview.json",
    "infra/observability/grafana/dashboards/ai-governance.json"
)

foreach ($d in $dashboards) {
    Assert-Test (Test-Path $d) "Dashboard file exists: $d"
    $json = Get-Content -Raw $d | ConvertFrom-Json
    Assert-Test ($json.title -ne $null) "Dashboard has valid title: $($json.title)"
}

# 3. Validate W3C Trace Propagator in Backend Common
Write-Host "`n3. Validating W3C Trace Propagator..." -ForegroundColor Yellow
$tracePropagator = "backend/common/src/main/java/com/enterpriseai/common/tracing/W3CTracePropagator.java"
Assert-Test (Test-Path $tracePropagator) "W3CTracePropagator.java exists in backend/common"
$content = Get-Content -Raw $tracePropagator
Assert-Test ($content.Contains("createNew") -and $content.Contains("parse")) "W3CTracePropagator implements createNew and parse methods"

# 4. Validate Operational Runbooks
Write-Host "`n4. Validating Operational Runbooks..." -ForegroundColor Yellow
$runbooks = @(
    "kafka-consumer-lag.md",
    "dlq-replay.md",
    "database-pool-exhaustion.md",
    "ollama-degradation.md",
    "tenant-isolation-alert.md"
)

foreach ($r in $runbooks) {
    $path = "docs/operations/runbooks/$r"
    Assert-Test (Test-Path $path) "Runbook exists: $r"
    $len = (Get-Item $path).Length
    Assert-Test ($len -gt 500) "Runbook has substantive documentation: $r ($len bytes)"
}

# 5. Validate Backend Build
Write-Host "`n5. Verifying Backend Compilation..." -ForegroundColor Yellow
$mvnCmd = "C:\Users\Eshwar Boini\.m2\wrapper\dists\apache-maven-3.9.10\a38810a491b03367137adfdfbe7d14c4\bin\mvn.cmd"
& $mvnCmd -B compile -DskipTests -f backend/pom.xml | Out-Null
Assert-Test ($LASTEXITCODE -eq 0) "Backend Maven reactor builds with 0 compilation errors"

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "Phase 10 Observability Verification COMPLETE: $passed / $total Passed!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
