"""
LangGraph Supervisor Orchestration Loop.
Executes the Multi-Agent state machine: Triage -> Retrieval -> Resolution -> Critic -> Action Execution.
Incorporates Workflow Versioning, Context Compression, Semantic Caching, and Telemetry.
"""
import json
import time
from typing import Dict, Any, Optional
from app.tools.ticket_tools import get_ticket, update_ticket_status, add_ticket_comment
from app.tools.workflow_tools import request_human_approval
from app.agents.triage_agent import run_triage
from app.agents.retrieval_agent import run_retrieval
from app.agents.resolution_agent import run_resolution
from app.agents.critic_agent import run_critic
from app.workflow_versions.workflow_registry import get_active_workflow_version, get_version_store
from app.workflow_versions.prompt_registry import PromptRegistry
from app.context.context_manager import ContextManager
from app.memory.semantic_cache import get_semantic_cache
from app.observability.metrics import emit_usage_event

def execute_supervisor_resolution(
    ticket_id: str,
    tenant_id: str,
    workflow_version: Optional[str] = None,
) -> Dict[str, Any]:
    """Orchestrates the autonomous multi-agent ticket resolution workflow."""
    start_time = time.time()
    active_wf = workflow_version or get_active_workflow_version()
    wf_config = get_version_store().get_workflow(active_wf) or {}
    prompt_ver = wf_config.get("prompt_version", "v2")
    prompts = PromptRegistry.get_prompt(prompt_ver)

    # Step 1: Read ticket from Core Ticketing Microservice
    ticket = get_ticket(ticket_id=ticket_id, tenant_id=tenant_id)

    # Check Semantic Cache for identical cached resolutions
    cache = get_semantic_cache()
    query = f"{ticket.get('subject', '')} {ticket.get('description', '')}"
    cached_resolution = cache.get(tenant_id, query)

    # Step 2: Triage and intent analysis
    triage = run_triage(ticket)

    # Step 3: Hybrid RAG Retrieval
    retrieval = run_retrieval(tenant_id=tenant_id, query=query)

    # Step 4: Context Assembly with Token Budget
    context_mgr = ContextManager(max_tokens=4096)
    assembled = context_mgr.assemble_context(
        system_policy=prompts.get("system_prompt", ""),
        tenant_config={"tenant_id": tenant_id, "auto_refund_limit": 50.0},
        customer_profile={"customer_id": ticket.get("customerId", "anon")},
        retrieved_chunks=[{"document_title": "Policy", "version": 1, "chunk_index": 0, "content": c} for c in retrieval.get("results", [])],
        conversation_history=[],
        current_ticket=ticket,
    )

    # Step 5: Resolution Formulation
    resolution = run_resolution(ticket=ticket, triage=triage, retrieval=retrieval)

    # Step 6: Critic & Guardrail validation
    critic = run_critic(resolution=resolution, retrieval=retrieval)

    if not critic.get("is_safe_to_dispatch", False):
        raise ValueError("Agent response failed safety and citation guardrails")

    # Step 7: Action Execution
    approval_record: Optional[Dict[str, Any]] = None
    plan = resolution.get("resolution_plan")

    if plan == "HUMAN_APPROVAL_REQUIRED":
        # Financial or high-risk: Route to Workflow Service
        approval_payload = json.dumps({
            "ticketSubject": ticket.get("subject"),
            "category": triage.get("intent_category"),
            "citations": resolution.get("citations"),
            "workflowVersion": active_wf,
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
            author_name=f"Autonomous AI Supervisor ({active_wf})",
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
            author_name=f"Autonomous AI Supervisor ({active_wf})",
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

    # Store in Semantic Cache
    cache.set(tenant_id, query, {
        "citations": resolution.get("citations"),
        "resolutionPlan": plan,
        "resolvedBy": active_wf,
    })

    # Telemetry and Metering Event
    elapsed_ms = (time.time() - start_time) * 1000
    tokens_est = assembled.get("estimated_tokens", 250)
    emit_usage_event(
        tenant_id=tenant_id,
        workflow_version=active_wf,
        tokens_used=tokens_est,
        latency_ms=elapsed_ms,
        model_name=wf_config.get("model_name", "llama3:8b"),
        outcome="SUCCESS",
        ticket_id=ticket_id,
    )

    return {
        "ticketId": ticket_id,
        "tenantId": tenant_id,
        "workflowVersion": active_wf,
        "triage": triage,
        "citations": resolution.get("citations"),
        "resolutionPlan": plan,
        "approval": approval_record,
        "finalStatus": updated_ticket.get("status"),
        "latencyMs": round(elapsed_ms, 2),
        "tokensEstimated": tokens_est,
    }
