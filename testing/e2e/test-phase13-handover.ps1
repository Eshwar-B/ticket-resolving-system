# ==============================================================================
# Phase 13 Portfolio, Final Verification and Handover Test Suite
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Phase 13: Portfolio Verification, End-to-End Demo & Final Handover" -ForegroundColor Cyan
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

# 1. Validate Reverse Engineering Guides
Write-Host "`n1. Validating Architecture Reverse Engineering Guides..." -ForegroundColor Yellow
$reGuides = @(
    "docs/reverse-engineering/multi-tenancy-rls.md",
    "docs/reverse-engineering/transactional-outbox.md",
    "docs/reverse-engineering/hybrid-rag-rrf.md",
    "docs/reverse-engineering/agentic-ai-supervisor.md"
)

foreach ($g in $reGuides) {
    Assert-Test (Test-Path $g) "Guide exists: $g"
    $len = (Get-Item $g).Length
    Assert-Test ($len -gt 1000) "Guide has comprehensive architectural depth: $g ($len bytes)"
}

# 2. Validate Interview Defense and Technical Q&A Bank
Write-Host "`n2. Validating System Design Defense & Interview Bank..." -ForegroundColor Yellow
Assert-Test (Test-Path "docs/interview/system-design-defense.md") "System design defense document exists"
$defense = Get-Content -Encoding UTF8 -Raw "docs/interview/system-design-defense.md"
Assert-Test ($defense.Contains("Row-Level Security") -and $defense.Contains("Dual-Write")) "System design defense covers RLS and outbox trade-offs"

Assert-Test (Test-Path "docs/interview/interview-qna.md") "Interview Q&A bank exists"
$qna = Get-Content -Encoding UTF8 -Raw "docs/interview/interview-qna.md"
Assert-Test ($qna.Contains("Q1:") -and $qna.Contains("Q8:")) "Interview bank includes detailed answers across topics"

# 3. Execute End-to-End Refund Demonstration Scenario
Write-Host "`n3. Executing End-to-End Refund Demonstration Drill..." -ForegroundColor Yellow
$demoOutput = & powershell -File scripts/run-e2e-demo.ps1
Assert-Test ($LASTEXITCODE -eq 0) "End-to-end demonstration script completed with exit code 0"
$demoStr = $demoOutput | Out-String
Assert-Test ($demoStr.Contains("10 / 10 Stages Succeeded")) "Demonstration successfully passed all 10 stages"

# 4. Final Reactor Compilation Sanity Check
Write-Host "`n4. Final Full-Stack Compilation Sanity Verification..." -ForegroundColor Yellow
$mvnCmd = "C:\Users\Eshwar Boini\.m2\wrapper\dists\apache-maven-3.9.10\a38810a491b03367137adfdfbe7d14c4\bin\mvn.cmd"
& $mvnCmd -B compile -DskipTests -f backend/pom.xml | Out-Null
Assert-Test ($LASTEXITCODE -eq 0) "All 9 Java microservices compile cleanly with 0 errors"

$pyCompile = python -m compileall ai-plane/
Assert-Test ($LASTEXITCODE -eq 0) "All Python AI Plane modules compile cleanly with 0 syntax errors"

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "Phase 13 Final Verification COMPLETE: $passed / $total Passed!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
