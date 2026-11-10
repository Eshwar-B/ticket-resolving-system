"""
Workflow Service Client Tools for Human Approvals.
Creates approval requests for high-risk, irreversible, or policy-constrained actions.
"""
import os
import httpx
from typing import Dict, Any, Optional

WORKFLOW_SERVICE_URL = os.getenv("WORKFLOW_SERVICE_URL", "http://localhost:8083")

def request_human_approval(tenant_id: str, ticket_id: str, action_type: str,
                           payload: str, requester: str = "ai-supervisor") -> Dict[str, Any]:
    """Submits an approval request to the Human Workflow Engine."""
    url = f"{WORKFLOW_SERVICE_URL}/api/v1/approvals"
    headers = {"X-Tenant-Id": tenant_id, "Content-Type": "application/json"}
    body = {
        "ticketId": ticket_id,
        "actionType": action_type,
        "payload": payload,
        "requester": requester
    }
    with httpx.Client(timeout=3.0) as client:
        resp = client.post(url, headers=headers, json=body)
        resp.raise_for_status()
        return resp.json()

def get_ticket_approvals(tenant_id: str, ticket_id: str) -> list:
    """Fetches all approval requests associated with a ticket."""
    url = f"{WORKFLOW_SERVICE_URL}/api/v1/approvals/ticket/{ticket_id}"
    headers = {"X-Tenant-Id": tenant_id}
    with httpx.Client(timeout=3.0) as client:
        resp = client.get(url, headers=headers)
        resp.raise_for_status()
        return resp.json()

