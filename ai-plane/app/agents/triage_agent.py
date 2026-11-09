"""
Triage Agent for AI Resolution Plane.
Extracts ticket intent, validates category, assesses urgency, and flags actions requiring escalation.
"""
from typing import Dict, Any

def run_triage(ticket: Dict[str, Any]) -> Dict[str, Any]:
    """Analyzes ticket subject, description, and metadata to classify intent and risk tier."""
    subject = ticket.get("subject", "").lower()
    description = ticket.get("description", "").lower()
    full_text = f"{subject} {description}"

    # Detect financial / irreversible keywords requiring Human-in-the-Loop approval
    financial_keywords = ["refund", "chargeback", "credit", "reimburse", "compensation", "payout", "cancel order", "restocking"]
    is_financial = any(kw in full_text for kw in financial_keywords)

    # Determine intent category
    if any(kw in full_text for kw in ["refund", "return", "broken", "damaged", "exchange"]):
        detected_category = "RETURN_REFUND"
    elif any(kw in full_text for kw in ["login", "password", "auth", "token", "access"]):
        detected_category = "ACCOUNT_ACCESS"
    elif any(kw in full_text for kw in ["slow", "bug", "crash", "error", "down", "offline"]):
        detected_category = "TECHNICAL_ISSUE"
    else:
        detected_category = ticket.get("category", "GENERAL_INQUIRY")

    # Assess urgency
    priority = ticket.get("priority", "MEDIUM")
    if any(kw in full_text for kw in ["urgent", "immediately", "outage", "production down", "critical"]):
        priority = "CRITICAL"

    return {
        "intent_category": detected_category,
        "is_financial_action": is_financial,
        "recommended_priority": priority,
        "requires_human_approval": is_financial
    }
