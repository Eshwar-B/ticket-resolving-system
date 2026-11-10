"""
Base Model Adapter Interface.
Defines canonical contract for LLM generation, structured generation, embeddings, and health checks.
"""
from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional, Type
from pydantic import BaseModel

class BaseModelAdapter(ABC):
    """Abstract interface for local and remote LLM / embedding providers."""

    @abstractmethod
    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        max_tokens: int = 512,
        temperature: float = 0.0,
    ) -> str:
        """Generates raw text completion."""
        pass

    @abstractmethod
    def generate_structured(
        self,
        prompt: str,
        schema: Type[BaseModel],
        system_prompt: Optional[str] = None,
    ) -> BaseModel:
        """Generates structured output conforming strictly to a Pydantic schema."""
        pass

    @abstractmethod
    def embed(self, texts: List[str]) -> List[List[float]]:
        """Generates dense vector embeddings for input text strings."""
        pass

    @abstractmethod
    def health_check(self) -> Dict[str, Any]:
        """Returns provider availability, latency, and loaded models."""
        pass
