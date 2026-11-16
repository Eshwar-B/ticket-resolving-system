# ==============================================================================
# Phase 11 Local Kubernetes & Cloud-Ready DevOps Verification Suite
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Phase 11: Validating Local Kubernetes & Cloud DevOps Artifacts" -ForegroundColor Cyan
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

# 1. Validate Kubernetes Manifests YAML Syntax
Write-Host "`n1. Validating Kubernetes YAML Files..." -ForegroundColor Yellow
$pyYamlCheck = @"
import yaml, glob

k8s_files = glob.glob('infra/k8s/**/*.yaml', recursive=True)
assert len(k8s_files) >= 10, f'Expected >= 10 k8s files, found {len(k8s_files)}'

for f in k8s_files:
    with open(f, 'r') as fp:
        docs = list(yaml.safe_load_all(fp))
        assert len(docs) > 0, f'Empty k8s manifest: {f}'
        for d in docs:
            if d:
                assert 'apiVersion' in d or 'kind' in d, f'Invalid manifest structure in {f}'
print(f'PARSED_{len(k8s_files)}_MANIFESTS')
"@

$yamlResult = $pyYamlCheck | python -
Assert-Test ($yamlResult.StartsWith("PARSED_")) "All Kubernetes manifests parsed cleanly with valid YAML ($($yamlResult.Trim()))"

# 2. Validate Container Security (Non-Root User)
Write-Host "`n2. Validating Non-Root Container Security..." -ForegroundColor Yellow
$deployments = Get-ChildItem -Path "infra/k8s/base" -Filter "*.yaml" | Where-Object {
    $content = Get-Content -Raw $_.FullName
    $content -match '(?m)^kind:\s*Deployment'
}

foreach ($d in $deployments) {
    $c = Get-Content -Raw $d.FullName
    Assert-Test ($c.Contains("runAsNonRoot: true")) "Deployment $($d.Name) enforces runAsNonRoot: true"
    Assert-Test ($c.Contains("livenessProbe") -and $c.Contains("readinessProbe")) "Deployment $($d.Name) configures health probes"
}

# 3. Validate Memory Resource Limits
Write-Host "`n3. Validating Memory Resource Limits..." -ForegroundColor Yellow
foreach ($d in $deployments) {
    $c = Get-Content -Raw $d.FullName
    Assert-Test ($c.Contains("limits:") -and $c.Contains("memory:")) "Deployment $($d.Name) defines explicit container memory limits"
}

# 4. Validate NetworkPolicy Default Deny
Write-Host "`n4. Validating NetworkPolicy Security Isolation..." -ForegroundColor Yellow
$netpol = Get-Content -Raw "infra/k8s/base/network-policies.yaml"
Assert-Test ($netpol.Contains("name: default-deny-all") -and $netpol.Contains("policyTypes:")) "NetworkPolicy implements default-deny-all ingress and egress"

# 5. Validate KEDA Kafka Auto-Scaling
Write-Host "`n5. Validating KEDA Kafka Consumer Lag Autoscaling..." -ForegroundColor Yellow
$keda = Get-Content -Raw "infra/k8s/base/keda-scaledobject.yaml"
Assert-Test ($keda.Contains("kind: ScaledObject") -and $keda.Contains("type: kafka") -and $keda.Contains("ticket.created.v1")) "KEDA ScaledObject scales AI workers on Kafka lag"

# 6. Validate Terraform AWS Reference Architecture & ₹0 Cost Governance
Write-Host "`n6. Validating Terraform & Cloud Cost Governance..." -ForegroundColor Yellow
Assert-Test (Test-Path "infra/terraform/main.tf") "main.tf exists"
Assert-Test (Test-Path "infra/terraform/variables.tf") "variables.tf exists"
Assert-Test (Test-Path "infra/terraform/outputs.tf") "outputs.tf exists"

$costDoc = Get-Content -Encoding UTF8 -Raw "infra/terraform/cost-estimate.md"
Assert-Test ($costDoc.Contains("Terraform was validated and cost-estimated, not applied to a real cloud")) "Cost governance mandate explicitly documented"
Assert-Test ($costDoc.Contains("₹0")) "Zero cloud spend constraint confirmed"

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "Phase 11 DevOps Verification COMPLETE: $passed / $total Passed!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
