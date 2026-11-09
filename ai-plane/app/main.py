"""
AI Plane FastAPI Entrypoint.
Provides REST triggers and health checks for the multi-agent resolution loop.
"""
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from typing import Optional, Dict, Any
from app.supervisor.supervisor_graph import execute_supervisor_resolution

app = FastAPI(
    title="Enterprise AI Resolution Plane",
    description="Multi-Agent LangGraph supervisor orchestrating RAG and Human-in-the-Loop workflows",
    version="0.1.0"
)

class ResolutionRequest(BaseModel):
    ticketId: str

@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "UP", "service": "ai-plane"}

@app.post("/api/v1/agent/resolve")
def resolve_ticket(
    request: ResolutionRequest,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-Id")
) -> Dict[str, Any]:
    if not x_tenant_id:
        raise HTTPException(status_code=400, detail="X-Tenant-Id header is required")

    try:
        result = execute_supervisor_resolution(ticket_id=request.ticketId, tenant_id=x_tenant_id)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
