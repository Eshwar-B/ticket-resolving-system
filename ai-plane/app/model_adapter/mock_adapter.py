"""
Deterministic Mock Model Adapter for local tests, CI, and zero-dependency execution.
Produces deterministic structured responses and 384-dimensional normalized embeddings.
"""
import hashlib
import json
import math
from typing import Dict, Any, List, Optional, Type
from pydantic import BaseModel
from app.model_adapter.base_adapter import BaseModelAdapter

class MockModelAdapter(BaseModelAdapter):
    """Deterministic mock model adapter."""

    def __init__(self, model_name: str = "mock-llama3-8b"):
        self.model_name = model_name
        self.embedding_dimension = 384

    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        max_tokens: int = 512,
        temperature: float = 0.0,
    ) -> str:
        prompt_lower = prompt.lower()
        if "refund" in prompt_lower or "chargeback" in prompt_lower:
            return (
                "Based on the company refund policy [Doc: \"Refund Policy\" v1 #chunk-0], "
                "refund requests require human approval. I have initiated the approval workflow."
            )
        elif "password" in prompt_lower or "reset" in prompt_lower:
            return (
                "To reset your password, visit the security portal as detailed in [Doc: \"Account Security Guide\" v1 #chunk-1]. "
                "Ensure your new password contains at least 12 characters."
            )
        else:
            return (
                f"Acknowledged request. Processed according to standard operating procedures. "
                f"Refer to knowledge base documentation for relevant guidance."
            )

    def generate_structured(
        self,
        prompt: str,
        schema: Type[BaseModel],
        system_prompt: Optional[str] = None,
    ) -> BaseModel:
        prompt_lower = prompt.lower()
        schema_fields = schema.model_fields.keys()

        data: Dict[str, Any] = {}
        if "intent_category" in schema_fields:
            if any(w in prompt_lower for w in ["refund", "chargeback", "damaged"]):
                data["intent_category"] = "RETURN_REFUND"
            elif any(w in prompt_lower for w in ["password", "login", "auth"]):
                data["intent_category"] = "ACCOUNT_ACCESS"
            else:
                data["intent_category"] = "GENERAL_INQUIRY"

        if "is_financial_action" in schema_fields:
            data["is_financial_action"] = any(w in prompt_lower for w in ["refund", "chargeback", "credit"])

        if "recommended_priority" in schema_fields:
            data["recommended_priority"] = "HIGH" if "urgent" in prompt_lower else "MEDIUM"

        if "requires_human_approval" in schema_fields:
            data["requires_human_approval"] = data.get("is_financial_action", False)

        if "confidence_score" in schema_fields:
            data["confidence_score"] = 0.95

        return schema.model_validate(data)

    def embed(self, texts: List[str]) -> List[List[float]]:
        results: List[List[float]] = []
        for text in texts:
            # Deterministic pseudo-embedding based on sha256 hash of tokens
            vec = [0.0] * self.embedding_dimension
            for i, word in enumerate(text.split()):
                h = int(hashlib.sha256(word.encode("utf-8")).hexdigest(), 16)
                idx = h % self.embedding_dimension
                val = ((h >> 8) % 1000) / 500.0 - 1.0
                vec[idx] += val

            # Normalize to unit vector
            norm = math.sqrt(sum(x * x for x in vec))
            if norm > 0:
                vec = [x / norm for x in vec]
            else:
                vec[0] = 1.0
            results.append(vec)
        return results

    def health_check(self) -> Dict[str, Any]:
        return {
            "status": "UP",
            "provider": "mock",
            "model": self.model_name,
            "embedding_dim": self.embedding_dimension,
            "latency_ms": 1.2,
        }
