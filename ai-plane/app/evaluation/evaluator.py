"""
Evaluator for Agentic AI Resolution.
Scores agent output against golden expected ground truth across multiple dimensions.
"""
from typing import Dict, Any

class TicketEvaluator:
    """Evaluates agent execution results against golden expectations."""

    @staticmethod
    def evaluate_case(actual: Dict[str, Any], expected: Dict[str, Any]) -> Dict[str, Any]:
        triage = actual.get("triage", {})
        plan = actual.get("resolutionPlan")
        citations = actual.get("citations", [])

        # 1. Intent Category Accuracy
        intent_match = (triage.get("intent_category") == expected.get("intent_category"))

        # 2. Financial / High-Risk Flag Accuracy
        financial_match = (triage.get("is_financial_action") == expected.get("is_financial_action"))

        # 3. Approval Routing Accuracy (Human-in-the-Loop)
        plan_match = (plan == expected.get("expected_plan"))

        # 4. Groundedness / Citations Presence
        citations_required = expected.get("citations_required", False)
        if citations_required:
            citation_grounded = len(citations) > 0 and all("[Doc:" in c for c in citations)
        else:
            citation_grounded = True

        # Calculate composite score (weighted: intent 30%, financial 25%, routing 25%, citation 20%)
        score = 0.0
        if intent_match:
            score += 0.30
        if financial_match:
            score += 0.25
        if plan_match:
            score += 0.25
        if citation_grounded:
            score += 0.20

        return {
            "intent_match": intent_match,
            "financial_match": financial_match,
            "plan_match": plan_match,
            "citation_grounded": citation_grounded,
            "score": round(score, 2),
            "passed": (score >= 0.75),
        }
