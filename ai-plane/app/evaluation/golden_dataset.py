"""
Golden Dataset for Autonomous Ticket Resolution Evaluation.
Defines canonical test cases with expected classifications, citations, risk flags, and actions.
"""
from typing import List, Dict, Any

GOLDEN_TICKETS: List[Dict[str, Any]] = [
    {
        "id": "golden-001",
        "tenant_id": "tenant-alpha",
        "subject": "Received empty package, requesting full refund",
        "description": "My delivery arrived today but the box was empty. I need a refund immediately.",
        "expected": {
            "intent_category": "RETURN_REFUND",
            "is_financial_action": True,
            "requires_human_approval": True,
            "citations_required": True,
            "expected_plan": "HUMAN_APPROVAL_REQUIRED",
        },
    },
    {
        "id": "golden-002",
        "tenant_id": "tenant-alpha",
        "subject": "Forgot password and cannot login to portal",
        "description": "I have been locked out of my corporate account. How can I reset my authentication credentials?",
        "expected": {
            "intent_category": "ACCOUNT_ACCESS",
            "is_financial_action": False,
            "requires_human_approval": False,
            "citations_required": True,
            "expected_plan": "AUTONOMOUS_RESOLVE",
        },
    },
    {
        "id": "golden-003",
        "tenant_id": "tenant-alpha",
        "subject": "URGENT: Production API returning 500 internal server errors",
        "description": "All customer endpoints are failing with 500 errors since 2pm. We are losing transactions.",
        "expected": {
            "intent_category": "TECHNICAL_ISSUE",
            "is_financial_action": False,
            "requires_human_approval": False,
            "recommended_priority": "CRITICAL",
            "citations_required": False,
            "expected_plan": "AUTONOMOUS_RESOLVE",
        },
    },
    {
        "id": "golden-004",
        "tenant_id": "tenant-alpha",
        "subject": "What are your standard business operating hours?",
        "description": "Can you please let me know when your customer support agents are available by phone?",
        "expected": {
            "intent_category": "GENERAL_INQUIRY",
            "is_financial_action": False,
            "requires_human_approval": False,
            "citations_required": False,
            "expected_plan": "AUTONOMOUS_RESOLVE",
        },
    },
    {
        "id": "golden-005",
        "tenant_id": "tenant-alpha",
        "subject": "Item arrived broken into pieces - reimburse me immediately",
        "description": "The ceramic vase arrived completely shattered. Issue a payout to my original card.",
        "expected": {
            "intent_category": "RETURN_REFUND",
            "is_financial_action": True,
            "requires_human_approval": True,
            "citations_required": True,
            "expected_plan": "HUMAN_APPROVAL_REQUIRED",
        },
    },
]

def get_golden_tickets() -> List[Dict[str, Any]]:
    return GOLDEN_TICKETS
