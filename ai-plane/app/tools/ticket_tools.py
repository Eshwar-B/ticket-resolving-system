"""
Ticket Service Client Tools for AI Resolution Plane.
Interacts with the Core Ticket Microservice over HTTP with strict tenant context.
"""
import os
import httpx
from typing import Optional, Dict, Any

TICKET_SERVICE_URL = os.getenv("TICKET_SERVICE_URL", "http://localhost:8081")

def get_ticket(ticket_id: str, tenant_id: str) -> Dict[str, Any]:
    """Fetches full ticket details including SLA, history, and status."""
    url = f"{TICKET_SERVICE_URL}/api/v1/tickets/{ticket_id}"
    headers = {"X-Tenant-Id": tenant_id}
    with httpx.Client(timeout=10.0) as client:
        resp = client.get(url, headers=headers)
        resp.raise_for_status()
        return resp.json()

def update_ticket_status(ticket_id: str, tenant_id: str, target_status: str, note: Optional[str] = None) -> Dict[str, Any]:
    """Applies a state machine transition to the ticket."""
    url = f"{TICKET_SERVICE_URL}/api/v1/tickets/{ticket_id}/status"
    headers = {"X-Tenant-Id": tenant_id, "Content-Type": "application/json"}
    body = {"targetStatus": target_status, "note": note or f"Updated by AI Agent to {target_status}"}
    with httpx.Client(timeout=10.0) as client:
        resp = client.patch(url, headers=headers, json=body)
        resp.raise_for_status()
        return resp.json()

def add_ticket_comment(ticket_id: str, tenant_id: str, author_id: str, author_name: str,
                       visibility: str, body: str) -> Dict[str, Any]:
    """Adds a public response or internal audit comment to the ticket."""
    url = f"{TICKET_SERVICE_URL}/api/v1/tickets/{ticket_id}/comments"
    headers = {"X-Tenant-Id": tenant_id, "Content-Type": "application/json"}
    payload = {
        "authorId": author_id,
        "authorName": author_name,
        "authorRole": "AGENT",
        "visibility": visibility,
        "body": body
    }
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(url, headers=headers, json=payload)
        resp.raise_for_status()
        return resp.json()
