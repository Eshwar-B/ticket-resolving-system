"""
Output Guardrail & Safety Gate.
Prevents ungrounded financial promises, leaks of internal data, and unauthorized commitments.
"""
import re
from typing import Dict, Any, List

# Unauthorized financial promises that require human authorization
UNAUTHORIZED_COMMITMENTS: List[re.Pattern] = [
    re.compile(r"(i\s+have|i\s+will|we\s+will|i've)\s+(refunded|credited|waived|issued\s+a\s+payout)", re.IGNORECASE),
    re.compile(r"(i\s+guarantee|i\s+promise)\s+(a\s+full\s+refund|compensation|you\s+money)", re.IGNORECASE),
    re.compile(r"your\s+account\s+has\s+been\s+credited\s+with\s+\$\d+", re.IGNORECASE),
]

class OutputGuardrail:
    """Validates generated agent outputs before transmission to customers."""

    @staticmethod
    def validate_output(
        response_text: str,
        resolution_plan: str,
        citations: List[str]
    ) -> Dict[str, Any]:
        violations = []

        # Rule 1: Check for unauthorized promises in autonomous responses
        if resolution_plan != "HUMAN_APPROVAL_REQUIRED":
            for pat in UNAUTHORIZED_COMMITMENTS:
                match = pat.search(response_text)
                if match:
                    violations.append(
                        f"Unauthorized financial commitment detected: '{match.group(0)}' without human approval"
                    )

        # Rule 2: Grounding check - if policy claims are made, citations must be non-empty
        if "policy" in response_text.lower() and not citations:
            violations.append("Response claims policy compliance but provides zero citations")

        # Rule 3: Check for internal leak indicators
        if "[Doc: internal_secret" in response_text or "app_user_password" in response_text:
            violations.append("Response contains leaked internal infrastructure identifiers")

        is_safe = len(violations) == 0

        return {
            "is_safe": is_safe,
            "violations": violations,
            "citations_count": len(citations),
        }
