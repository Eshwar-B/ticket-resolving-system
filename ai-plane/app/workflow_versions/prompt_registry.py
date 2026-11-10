"""
Versioned Prompt Template Registry.
Maintains versioned system and task prompt templates with audit metadata.
"""
from typing import Dict, Any, Optional

PROMPT_VERSIONS: Dict[str, Dict[str, Any]] = {
    "v1": {
        "version": "v1",
        "name": "baseline-triage-and-resolution",
        "description": "Initial zero-shot baseline prompt for triage and hybrid resolution",
        "created_at": "2026-11-01T00:00:00Z",
        "system_prompt": (
            "You are an enterprise AI support resolution agent. "
            "You must follow tenant data boundaries, inspect retrieved documentation, "
            "and cite all policy facts using [Doc: \"<Title>\" v<version> #chunk-<id>]. "
            "Never perform financial actions without human approval."
        ),
        "triage_instructions": "Identify customer intent category and assess risk tier.",
        "resolution_instructions": "Synthesize a concise, grounded response with exact citations.",
        "critic_instructions": "Verify that every claim is grounded in retrieved chunks.",
    },
    "v2": {
        "version": "v2",
        "name": "strict-citation-and-grounding",
        "description": "Enhanced few-shot prompt with strict hallucination penalization and SLA urgency detection",
        "created_at": "2026-11-09T00:00:00Z",
        "system_prompt": (
            "You are a certified Enterprise Support Specialist operating under zero-trust guidelines. "
            "Strict rules:\n"
            "1. Grounding: All assertions must cite retrieved context: [Doc: \"<Title>\" v<version> #chunk-<id>].\n"
            "2. Approvals: Any refund, reimbursement, or balance mutation REQUIRES human supervisor sign-off.\n"
            "3. Multi-tenancy: Strictly reject referencing any data from other tenants.\n"
            "4. Tone: Professional, empathetic, direct."
        ),
        "triage_instructions": "Extract entities, classify intent, check SLA deadlines, and flag high-risk actions.",
        "resolution_instructions": "Draft public response with full citations and generate internal execution notes.",
        "critic_instructions": "Reject response if citations are missing or do not match retrieved document ids.",
    },
}

class PromptRegistry:
    """Provides access to versioned prompt templates."""

    @staticmethod
    def get_prompt(version: str = "v2") -> Dict[str, Any]:
        if version not in PROMPT_VERSIONS:
            version = "v2"
        return PROMPT_VERSIONS[version]

    @staticmethod
    def list_versions() -> list[str]:
        return list(PROMPT_VERSIONS.keys())
