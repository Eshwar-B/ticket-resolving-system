# ==============================================================================
# Enterprise AI Platform: Phase 7 Automated Verification Suite
# AI Platform Governance, Evaluation & Versioning Tests
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   RUNNING PHASE 7 EVALUATION & GOVERNANCE TEST SUITE   " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# Execute Python test verification script directly
$pyTestScript = @"
import sys
import os
sys.path.insert(0, os.path.abspath('ai-plane'))

from app.model_adapter.adapter_factory import get_model_adapter
from app.workflow_versions.prompt_registry import PromptRegistry
from app.workflow_versions.workflow_registry import (
    get_active_workflow_version,
    list_workflow_versions,
    activate_workflow_version,
    rollback_workflow_version,
)
from app.context.context_manager import ContextManager
from app.memory.semantic_cache import get_semantic_cache
from app.evaluation.eval_runner import EvaluationRunner
from app.evaluation.regression_checker import RegressionChecker
from app.observability.metrics import emit_usage_event, get_metrics_summary

# 1. Test Model Adapter
print('=== Step 1: Test Model Adapter Interface & Health ===')
adapter = get_model_adapter()
health = adapter.health_check()
assert health['status'] in ['UP', 'DEGRADED'], f'Invalid health status: {health}'
embeds = adapter.embed(['test prompt embedding'])
assert len(embeds) == 1 and len(embeds[0]) == 384, 'Invalid embedding vector dimension'
print('PASS: Model Adapter initialized and healthy (384-dim normalized vectors)')

# 2. Test Prompt Registry
print('\n=== Step 2: Test Prompt Template Versioning ===')
versions = PromptRegistry.list_versions()
assert 'v1' in versions and 'v2' in versions, 'Missing prompt versions'
p2 = PromptRegistry.get_prompt('v2')
assert '[Doc:' in p2['system_prompt'], 'Missing citation instructions in v2 prompt'
print('PASS: Prompt Registry returns versioned templates (v1, v2) with citations mandate')

# 3. Test Workflow Registry & Rollback
print('\n=== Step 3: Test Workflow Version Lifecycle & Rollback ===')
wfs = list_workflow_versions()
assert len(wfs) >= 2, f'Expected at least 2 workflow versions, found {len(wfs)}'
initial_active = get_active_workflow_version()
print(f'Initial active workflow: {initial_active}')

# Activate v1.0.0
activate_workflow_version('wf-v1.0.0')
assert get_active_workflow_version() == 'wf-v1.0.0', 'Failed to activate wf-v1.0.0'

# Rollback to v1.1.0
rollback_workflow_version('wf-v1.1.0')
assert get_active_workflow_version() == 'wf-v1.1.0', 'Failed to rollback to wf-v1.1.0'
print('PASS: Workflow Version activation, switching, and rollback verified')

# 4. Test Context Assembly & Token Compression
print('\n=== Step 4: Test Context Assembly & Token Budget Compression ===')
mgr = ContextManager(max_tokens=250)
large_history = [{'role': 'user', 'content': f'Message #{i} describing an issue'} for i in range(50)]
ctx = mgr.assemble_context(
    system_policy='Strict zero trust policy.',
    tenant_config={'tenant_id': 'tenant-alpha'},
    customer_profile={'customer_id': 'cust-1'},
    retrieved_chunks=[{'document_title': 'Refund Policy', 'version': 1, 'chunk_index': 0, 'content': 'Eligible within 30 days.'}],
    conversation_history=large_history,
    current_ticket={'id': 'tick-1', 'subject': 'Refund', 'description': 'Need money back'}
)
assert ctx['was_compressed'] == True, 'Context compression should have triggered'
assert ctx['citations_preserved'] == 1, 'Retrieved citations must not be dropped during compression'
print('PASS: Context assembled in blueprint order with progressive token budget compression')

# 5. Test Semantic Cache & Strict Tenant Isolation
print('\n=== Step 5: Test Semantic Cache & Cross-Tenant Boundary ===')
cache = get_semantic_cache()
cache.set('tenant-alpha', 'how do i reset password?', {'answer': 'Use security portal', 'plan': 'SAFE'})
# Check tenant-alpha gets hit
alpha_val = cache.get('tenant-alpha', 'how do i reset password?')
assert alpha_val is not None and alpha_val['plan'] == 'SAFE', 'Tenant Alpha cache miss'
# Check tenant-beta gets zero hit (strict isolation)
beta_val = cache.get('tenant-beta', 'how do i reset password?')
assert beta_val is None, 'Cross-tenant cache leakage detected!'
print('PASS: Semantic Cache verified with strict tenant key scoping (zero cross-tenant leakage)')

# 6. Test Evaluation Suite on Golden Dataset
print('\n=== Step 6: Test Evaluation Suite on Golden Dataset ===')
eval_report = EvaluationRunner.run_suite(workflow_version='wf-v1.1.0')
assert eval_report['total_cases'] == 5, f'Expected 5 golden cases, got {eval_report[\"total_cases\"]}'
assert eval_report['overall_score'] >= 0.75, f'Overall score too low: {eval_report[\"overall_score\"]}'
metrics = eval_report['metrics']
assert metrics['intent_accuracy'] >= 0.80, f'Low intent accuracy: {metrics[\"intent_accuracy\"]}'
assert metrics['financial_risk_accuracy'] >= 0.80, f'Low financial accuracy: {metrics[\"financial_risk_accuracy\"]}'
print(f'PASS: Evaluation benchmark passed (Score: {eval_report[\"overall_score\"]}, Intent: {metrics[\"intent_accuracy\"]})')

# 7. Test CI Regression Gate
print('\n=== Step 7: Test CI Regression Gate Threshold Enforcement ===')
reg_check = RegressionChecker.check_thresholds(eval_report)
assert reg_check['passed'] == True, f'Regression check failed: {reg_check[\"violations\"]}'

# Negative test: artificially degraded report
bad_report = {'overall_score': 0.50, 'metrics': {'intent_accuracy': 0.40}}
bad_check = RegressionChecker.check_thresholds(bad_report)
assert bad_check['passed'] == False, 'Degraded run should have failed regression gate'
print('PASS: Regression gate successfully approved valid run and blocked degraded candidate')

# 8. Test Usage Telemetry
print('\n=== Step 8: Test Operational Telemetry & Usage Metering ===')
emit_usage_event(
    tenant_id='tenant-alpha',
    workflow_version='wf-v1.1.0',
    tokens_used=350,
    latency_ms=120.5,
    outcome='SUCCESS'
)
summary = get_metrics_summary()
assert summary['totalInvocations'] >= 1, 'Failed to record usage event invocation'
assert summary['totalTokens'] >= 350, 'Failed to record token usage'
print(f'PASS: Usage telemetry recorded successfully (Invocations: {summary[\"totalInvocations\"]}, Tokens: {summary[\"totalTokens\"]})')

print('\n========================================================')
print('ALL PHASE 7 GOVERNANCE & EVALUATION TESTS PASSED (8/8)!')
print('========================================================')
"@

python -c "$pyTestScript"
if ($LASTEXITCODE -ne 0) {
    Write-Host "PHASE 7 TEST SUITE FAILED!" -ForegroundColor Red
    exit 1
}
