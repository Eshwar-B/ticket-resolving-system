"""
Knowledge Service Client Tools for AI Resolution Plane.
Queries the Hybrid RAG Engine (PostgreSQL FTS + Qdrant vectors + RRF) with strict tenant isolation.
"""
import os
import httpx
from typing import Dict, Any, List

KNOWLEDGE_SERVICE_URL = os.getenv("KNOWLEDGE_SERVICE_URL", "http://localhost:8082")

def search_knowledge(tenant_id: str, query: str, top_k: int = 3) -> Dict[str, Any]:
    """Queries tenant-isolated knowledge chunks and returns grounded citations."""
    url = f"{KNOWLEDGE_SERVICE_URL}/api/v1/knowledge/search"
    headers = {"X-Tenant-Id": tenant_id, "Content-Type": "application/json"}
    payload = {"query": query, "topK": top_k}
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(url, headers=headers, json=payload)
        resp.raise_for_status()
        return resp.json()
