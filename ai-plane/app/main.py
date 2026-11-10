"""
AI Plane FastAPI Entrypoint.
Provides REST triggers, workflow governance, evaluation runs, and health checks.
"""
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from typing import Optional, Dict, Any, List
from app.supervisor.supervisor_graph import execute_supervisor_resolution
from app.model_adapter.adapter_factory import get_model_adapter
from app.workflow_versions.workflow_registry import (
    get_active_workflow_version,
    list_workflow_versions,
    activate_workflow_version,
    rollback_workflow_version,
)
from app.evaluation.eval_runner import EvaluationRunner
from app.evaluation.regression_checker import RegressionChecker
from app.observability.metrics import get_metrics_summary

app = FastAPI(
    title="Enterprise AI Resolution Plane & Governance",
    description="Multi-Agent LangGraph supervisor orchestrating RAG, Governance, and Evaluation",
    version="0.2.0"
)

class ResolutionRequest(BaseModel):
    ticketId: str
    workflowVersion: Optional[str] = None

class EvaluateRequest(BaseModel):
    workflowVersion: Optional[str] = None

@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "UP", "service": "ai-plane"}

@app.get("/api/v1/models/health")
def model_health() -> Dict[str, Any]:
    adapter = get_model_adapter()
    return adapter.health_check()

@app.get("/api/v1/workflows/versions")
def get_versions() -> Dict[str, Any]:
    return {
        "activeVersion": get_active_workflow_version(),
        "versions": list_workflow_versions(),
    }

@app.post("/api/v1/workflows/versions/{version_id}/activate")
def activate_version(version_id: str) -> Dict[str, Any]:
    try:
        return activate_workflow_version(version_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))

@app.post("/api/v1/workflows/versions/{version_id}/rollback")
def rollback_version(version_id: str) -> Dict[str, Any]:
    try:
        return rollback_workflow_version(version_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))

@app.post("/api/v1/workflows/evaluate")
def run_evaluation(request: Optional[EvaluateRequest] = None) -> Dict[str, Any]:
    wf_ver = request.workflowVersion if request and request.workflowVersion else get_active_workflow_version()
    report = EvaluationRunner.run_suite(workflow_version=wf_ver)
    reg_check = RegressionChecker.check_thresholds(report)
    return {
        "report": report,
        "regressionGate": reg_check,
    }

@app.get("/api/v1/metrics/usage")
def get_usage_telemetry() -> Dict[str, Any]:
    return get_metrics_summary()

@app.post("/api/v1/agent/resolve")
def resolve_ticket(
    request: ResolutionRequest,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-Id")
) -> Dict[str, Any]:
    if not x_tenant_id:
        raise HTTPException(status_code=400, detail="X-Tenant-Id header is required")

    try:
        result = execute_supervisor_resolution(
            ticket_id=request.ticketId,
            tenant_id=x_tenant_id,
            workflow_version=request.workflowVersion,
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
