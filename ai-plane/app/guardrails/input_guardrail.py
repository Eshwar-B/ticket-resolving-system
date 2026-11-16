"""
Input Guardrail & Prompt Injection Sanitizer.
Detects direct prompt injections, jailbreaks, system prompt leak attempts, and masks PII.
"""
import re
from typing import Dict, Any, Tuple, List

# Known prompt injection & jailbreak heuristic patterns
INJECTION_PATTERNS: List[re.Pattern] = [
    re.compile(r"ignore\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)", re.IGNORECASE),
    re.compile(r"disregard\s+(all\s+)?(previous|prior|system)\s+(instructions|directives)", re.IGNORECASE),
    re.compile(r"(reveal|print|show|output|leak)\s+(your\s+)?(system\s+prompt|initial\s+instructions|secret\s+key)", re.IGNORECASE),
    re.compile(r"you\s+are\s+now\s+(DAN|unrestricted|in\s+developer\s+mode|jailbroken)", re.IGNORECASE),
    re.compile(r"act\s+as\s+an\s+unfiltered\s+AI", re.IGNORECASE),
    re.compile(r"bypass\s+(security|safety|guidelines|policies)", re.IGNORECASE),
    re.compile(r"drop\s+table|delete\s+from|union\s+select", re.IGNORECASE),
]

# PII regex patterns
SSN_PATTERN = re.compile(r"\b\d{3}-\d{2}-\d{4}\b")
CREDIT_CARD_PATTERN = re.compile(r"\b(?:\d{4}[-\s]?){3}\d{4}\b")
EMAIL_PATTERN = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b")
PHONE_PATTERN = re.compile(r"\b(?:\+?1[-.]?)?\(?\d{3}\)?[-.]?\d{3}[-.]?\d{4}\b")

class InputGuardrail:
    """Enforces prompt safety, injection defense, and PII masking on user inputs."""

    @staticmethod
    def detect_prompt_injection(text: str) -> Tuple[bool, str]:
        """Scans input for common jailbreak, injection, and override attempts."""
        for pattern in INJECTION_PATTERNS:
            match = pattern.search(text)
            if match:
                return True, f"Prompt injection detected matching rule: '{match.group(0)}'"
        return False, ""

    @staticmethod
    def mask_pii(text: str) -> Tuple[str, Dict[str, int]]:
        """Redacts sensitive PII from incoming text before feeding to model or RAG."""
        redactions = {"ssn": 0, "credit_card": 0, "email": 0, "phone": 0}

        def count_and_replace(pattern: re.Pattern, mask_tag: str, count_key: str, s: str) -> str:
            matches = pattern.findall(s)
            if matches:
                redactions[count_key] += len(matches)
                return pattern.sub(mask_tag, s)
            return s

        sanitized = text
        sanitized = count_and_replace(CREDIT_CARD_PATTERN, "[REDACTED_CARD]", "credit_card", sanitized)
        sanitized = count_and_replace(SSN_PATTERN, "[REDACTED_SSN]", "ssn", sanitized)
        sanitized = count_and_replace(EMAIL_PATTERN, "[REDACTED_EMAIL]", "email", sanitized)
        sanitized = count_and_replace(PHONE_PATTERN, "[REDACTED_PHONE]", "phone", sanitized)

        return sanitized, redactions

    @staticmethod
    def sanitize(text: str) -> Dict[str, Any]:
        """Complete input safety pipeline."""
        is_injection, reason = InputGuardrail.detect_prompt_injection(text)
        masked_text, redactions = InputGuardrail.mask_pii(text)

        return {
            "is_safe": not is_injection,
            "injection_detected": is_injection,
            "rejection_reason": reason,
            "sanitized_text": masked_text,
            "pii_redacted": redactions,
        }
