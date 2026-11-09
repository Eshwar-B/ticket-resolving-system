"""
LangGraph Supervisor Orchestration Loop.
Executes the Multi-Agent state machine: Triage -> Retrieval -> Resolution -> Critic -> Action Execution.
"""
import json
from typing import Dict, Any, Optional
from app.tools.ticket_tools import get_ticket, update_ticket_status, add_ticket_comment
from app.tools.workflow_tools import request_human_approval
from app.agents.triage_agent import run_triage
from app.agents.retrieval_agent import run_retrieval
from app.agents.resolution_agent import run_resolution
from app.agents.critic_agent import run_critic

def execute_supervisor_resolution(ticket_id: str, tenant_id: str) -> Dict[str, Any]:
    """Orchestrates the autonomous multi-agent ticket resolution workflow."""
    # Step 1: Read ticket from Core Ticketing Microservice
    ticket = get_ticket(ticket_id=ticket_id, tenant_id=tenant_id)

    # Step 2: Triage and intent analysis
    triage = run_triage(ticket)

    # Step 3: Hybrid RAG Retrieval
    query = f"{ticket.get('subject', '')} {ticket.get('description', '')}"
    retrieval = run_retrieval(tenant_id=tenant_id, query=query)

    # Step 4: Resolution Formulation
    resolution = run_resolution(ticket=ticket, triage=triage, retrieval=retrieval)

    # Step 5: Critic & Guardrail validation
    critic = run_critic(resolution=resolution, retrieval=retrieval)

    if not critic.get("is_safe_to_dispatch", False):
        raise ValueError("Agent response failed safety and citation guardrails")

    # Step 6: Action Execution
    approval_record: Optional[Dict[str, Any]] = None
    plan = resolution.get("resolution_plan")

    if plan == "HUMAN_APPROVAL_REQUIRED":
        # Financial or high-risk: Route to Workflow Service
        approval_payload = json.dumps({
            "ticketSubject": ticket.get("subject"),
            "category": triage.get("intent_category"),
            "citations": resolution.get("citations")
        })
        approval_record = request_human_approval(
            tenant_id=tenant_id,
            ticket_id=ticket_id,
            action_type=resolution.get("action_type", "REFUND_APPROVAL"),
            payload=approval_payload,
            requester="ai-supervisor"
        )

        # Log internal audit note
        add_ticket_comment(
            ticket_id=ticket_id,
            tenant_id=tenant_id,
            author_id="ai-supervisor",
            author_name="Autonomous AI Supervisor",
            visibility="INTERNAL",
            body=resolution.get("internal_note")
        )

        # Post public customer explanation
        add_ticket_comment(
            ticket_id=ticket_id,
            tenant_id=tenant_id,
            author_id="ai-supervisor",
            author_name="Support Specialist",
            visibility="PUBLIC",
            body=resolution.get("public_response")
        )

        # Transition ticket status: NEW -> OPEN -> PENDING (waiting for human approval)
        current_status = ticket.get("status")
        if current_status == "NEW":
            update_ticket_status(ticket_id, tenant_id, "OPEN", "Opened by AI Supervisor")
        update_ticket_status(ticket_id, tenant_id, "PENDING", "Awaiting human approval from workflow engine")

    else:
        # Safe procedural: Autonomous resolution
        # Log internal audit note
        add_ticket_comment(
            ticket_id=ticket_id,
            tenant_id=tenant_id,
            author_id="ai-supervisor",
            author_name="Autonomous AI Supervisor",
            visibility="INTERNAL",
            body=resolution.get("internal_note")
        )

        # Post public customer resolution
        add_ticket_comment(
            ticket_id=ticket_id,
            tenant_id=tenant_id,
            author_id="ai-supervisor",
            author_name="Support Specialist",
            visibility="PUBLIC",
            body=resolution.get("public_response")
        )

        # Transition ticket status: NEW -> OPEN -> IN_PROGRESS -> RESOLVED
        current_status = ticket.get("status")
        if current_status == "NEW":
            update_ticket_status(ticket_id, tenant_id, "OPEN", "Opened by AI Supervisor")
            update_ticket_status(ticket_id, tenant_id, "IN_PROGRESS", "Resolution in progress")
        elif current_status == "OPEN":
            update_ticket_status(ticket_id, tenant_id, "IN_PROGRESS", "Resolution in progress")

        update_ticket_status(ticket_id, tenant_id, "RESOLVED", "Resolved autonomously by AI Supervisor")

    # Fetch updated ticket
    updated_ticket = get_ticket(ticket_id=ticket_id, tenant_id=tenant_id)

    return {
        "ticketId": ticket_id,
        "tenantId": tenant_id,
        "triage": triage,
        "citations": resolution.get("citations"),
        "resolutionPlan": plan,
        "approval": approval_record,
        "finalStatus": updated_ticket.get("status")
    }
