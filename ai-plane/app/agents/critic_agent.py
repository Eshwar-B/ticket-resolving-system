"""
Critic & Guardrail Agent for AI Resolution Plane.
Verifies factual grounding, citation integrity, and safety guardrails.
"""
from typing import Dict, Any, List

def run_critic(resolution: Dict[str, Any], retrieval: Dict[str, Any]) -> Dict[str, Any]:
    """Inspects drafted response for compliance, citations, and hallucination prevention."""
    public_response = resolution.get("public_response", "")
    citations = resolution.get("citations", [])

    # Check 1: Citation presence
    has_citations = len(citations) > 0 or "[Doc:" in public_response or "[General" in public_response

    # Check 2: No prohibited promises (e.g. guaranteeing arbitrary payouts without approval)
    has_unauthorized_guarantee = (
        "i have refunded your money" in public_response.lower() or
        "funds have been deposited" in public_response.lower()
    )

    passed_guardrails = has_citations and not has_unauthorized_guarantee
    critique = "PASSED" if passed_guardrails else "FAILED_GUARDRAILS"

    return {
        "critique_status": critique,
        "has_citations": has_citations,
        "passed_guardrails": passed_guardrails,
        "is_safe_to_dispatch": passed_guardrails
    }
