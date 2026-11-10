"""
Model Adapter Factory.
Provides singleton model adapter based on MODEL_ADAPTER environment variable (default: mock).
"""
import os
from typing import Optional
from app.model_adapter.base_adapter import BaseModelAdapter
from app.model_adapter.mock_adapter import MockModelAdapter
from app.model_adapter.ollama_adapter import OllamaModelAdapter

_current_adapter: Optional[BaseModelAdapter] = None

def get_model_adapter() -> BaseModelAdapter:
    """Returns the configured model adapter instance."""
    global _current_adapter
    if _current_adapter is None:
        adapter_type = os.getenv("MODEL_ADAPTER", "mock").lower()
        if adapter_type == "ollama":
            base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
            model_name = os.getenv("OLLAMA_MODEL", "llama3:8b")
            _current_adapter = OllamaModelAdapter(base_url=base_url, model_name=model_name)
        else:
            _current_adapter = MockModelAdapter()
    return _current_adapter

def set_model_adapter(adapter: BaseModelAdapter) -> None:
    """Explicitly override model adapter (useful for testing)."""
    global _current_adapter
    _current_adapter = adapter
