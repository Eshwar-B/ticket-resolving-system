"""
Resolution Agent for AI Resolution Plane.
Synthesizes retrieved RAG context and ticket inquiry into a structured resolution plan.
Routes financial actions to Human Approvals and formulates grounded response text.
"""
from typing import Dict, Any, List

def run_resolution(ticket: Dict[str, Any], triage: Dict[str, Any],
                   retrieval: Dict[str, Any]) -> Dict[str, Any]:
    """Generates the resolution strategy, public response text, and internal audit notes."""
    subject = ticket.get("subject", "")
    citations = retrieval.get("citations", [])
    citation_str = " ".join(citations) if citations else "[General Support Policy]"
    snippets = retrieval.get("retrieved_snippets", [])

    is_financial = triage.get("is_financial_action", False)
    category = triage.get("intent_category", "GENERAL")

    if is_financial:
        # Require human approval for financial refund / credit
        plan = "HUMAN_APPROVAL_REQUIRED"
        action_type = "REFUND_APPROVAL"
        public_message = (
            f"Thank you for contacting us regarding '{subject}'. "
            f"Under our policy {citation_str}, your request has been routed to a senior specialist "
            f"for formal authorization. You will receive an update as soon as the review is complete."
        )
        internal_note = (
            f"AI Supervisor: Ticket flagged for financial approval. "
            f"Detected category: {category}. Policy citations: {citation_str}. "
            f"Submitting approval request to Workflow Service."
        )
    else:
        # Safe procedural / informational resolution
        plan = "AUTONOMOUS_RESOLVE"
        action_type = "DIRECT_RESOLVE"
        policy_context = snippets[0] if snippets else "standard operating procedure"
        public_message = (
            f"Hello, regarding your inquiry on '{subject}': "
            f"According to {citation_str}, {policy_context}. "
            f"Please let us know if you need any further assistance!"
        )
        internal_note = (
            f"AI Supervisor: Ticket resolved autonomously using verified knowledge base. "
            f"Citations used: {citation_str}."
        )

    return {
        "resolution_plan": plan,
        "action_type": action_type,
        "public_response": public_message,
        "internal_note": internal_note,
        "citations": citations
    }

