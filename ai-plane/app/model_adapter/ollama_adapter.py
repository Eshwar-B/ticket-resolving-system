"""
Ollama HTTP Model Adapter.
Connects to local Ollama instance (default: http://localhost:11434).
Includes graceful degradation and fallback to MockModelAdapter when Ollama is unreachable.
"""
import json
import logging
from typing import Dict, Any, List, Optional, Type
import httpx
from pydantic import BaseModel
from app.model_adapter.base_adapter import BaseModelAdapter
from app.model_adapter.mock_adapter import MockModelAdapter

logger = logging.getLogger(__name__)

class OllamaModelAdapter(BaseModelAdapter):
    """Local Ollama client adapter with graceful degradation."""

    def __init__(
        self,
        base_url: str = "http://localhost:11434",
        model_name: str = "llama3:8b",
        embed_model: str = "all-minilm:latest",
        timeout_seconds: float = 5.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.model_name = model_name
        self.embed_model = embed_model
        self.timeout = timeout_seconds
        self._fallback = MockModelAdapter(model_name=f"fallback-{model_name}")

    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        max_tokens: int = 512,
        temperature: float = 0.0,
    ) -> str:
        try:
            payload = {
                "model": self.model_name,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "num_predict": max_tokens,
                    "temperature": temperature,
                },
            }
            if system_prompt:
                payload["system"] = system_prompt

            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(f"{self.base_url}/api/generate", json=payload)
                if res.status_code == 200:
                    return res.json().get("response", "")
        except Exception as e:
            logger.warning("Ollama unavailable, falling back to mock adapter: %s", e)

        return self._fallback.generate(prompt, system_prompt, max_tokens, temperature)

    def generate_structured(
        self,
        prompt: str,
        schema: Type[BaseModel],
        system_prompt: Optional[str] = None,
    ) -> BaseModel:
        try:
            sys_instruct = (
                f"{system_prompt or ''}\nRespond ONLY in valid JSON matching this schema: "
                f"{json.dumps(schema.model_json_schema())}"
            )
            raw_text = self.generate(prompt=prompt, system_prompt=sys_instruct, temperature=0.0)
            data = json.loads(raw_text)
            return schema.model_validate(data)
        except Exception as e:
            logger.warning("Structured generation fallback triggered: %s", e)
            return self._fallback.generate_structured(prompt, schema, system_prompt)

    def embed(self, texts: List[str]) -> List[List[float]]:
        embeddings: List[List[float]] = []
        try:
            with httpx.Client(timeout=self.timeout) as client:
                for t in texts:
                    res = client.post(
                        f"{self.base_url}/api/embeddings",
                        json={"model": self.embed_model, "prompt": t},
                    )
                    if res.status_code == 200:
                        embeddings.append(res.json().get("embedding", []))
                    else:
                        raise RuntimeError(f"Ollama embedding status: {res.status_code}")
                return embeddings
        except Exception as e:
            logger.warning("Ollama embeddings failed, using fallback: %s", e)
            return self._fallback.embed(texts)

    def health_check(self) -> Dict[str, Any]:
        try:
            with httpx.Client(timeout=2.0) as client:
                res = client.get(f"{self.base_url}/api/tags")
                if res.status_code == 200:
                    models = [m.get("name") for m in res.json().get("models", [])]
                    return {
                        "status": "UP",
                        "provider": "ollama",
                        "base_url": self.base_url,
                        "available_models": models,
                    }
        except Exception as e:
            return {
                "status": "DEGRADED",
                "provider": "ollama",
                "message": f"Ollama not reachable ({str(e)}), fallback active",
                "fallback": self._fallback.health_check(),
            }
        return {"status": "UNKNOWN"}
