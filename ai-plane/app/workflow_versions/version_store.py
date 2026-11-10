"""
Persistence and In-Memory Storage for Workflow Versions.
Allows runtime activation, rollback, and Canary comparison across workflow versions.
"""
import json
import os
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

DEFAULT_STATE_FILE = os.path.join(os.path.dirname(__file__), "workflow_state.json")

INITIAL_WORKFLOWS: Dict[str, Dict[str, Any]] = {
    "wf-v1.0.0": {
        "version_id": "wf-v1.0.0",
        "name": "Standard Autonomous Resolution",
        "prompt_version": "v1",
        "model_name": "llama3:8b",
        "is_active": False,
        "traffic_percentage": 0,
        "deployed_at": "2026-11-01T10:00:00Z",
        "changelog": "Initial baseline multi-agent loop with basic triage and RAG retrieval",
    },
    "wf-v1.1.0": {
        "version_id": "wf-v1.1.0",
        "name": "Strict Guardrails & RRF Hybrid RAG",
        "prompt_version": "v2",
        "model_name": "llama3:8b",
        "is_active": True,
        "traffic_percentage": 100,
        "deployed_at": "2026-11-09T12:00:00Z",
        "changelog": "Added critic agent verification, strict citations, and human approval for financial refund requests",
    },
}

class VersionStore:
    """Stores and retrieves active workflow versions with state persistence."""

    def __init__(self, state_file: str = DEFAULT_STATE_FILE):
        self.state_file = state_file
        self._workflows = dict(INITIAL_WORKFLOWS)
        self._active_version = "wf-v1.1.0"
        self._load()

    def _load(self) -> None:
        if os.path.exists(self.state_file):
            try:
                with open(self.state_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self._workflows = data.get("workflows", self._workflows)
                    self._active_version = data.get("active_version", self._active_version)
            except Exception:
                pass

    def _save(self) -> None:
        try:
            with open(self.state_file, "w", encoding="utf-8") as f:
                json.dump({
                    "active_version": self._active_version,
                    "workflows": self._workflows,
                    "updated_at": datetime.now(timezone.utc).isoformat(),
                }, f, indent=2)
        except Exception:
            pass

    def get_active_version(self) -> str:
        return self._active_version

    def get_workflow(self, version_id: str) -> Optional[Dict[str, Any]]:
        return self._workflows.get(version_id)

    def list_workflows(self) -> List[Dict[str, Any]]:
        return list(self._workflows.values())

    def activate_version(self, version_id: str) -> Dict[str, Any]:
        if version_id not in self._workflows:
            raise ValueError(f"Workflow version {version_id} does not exist")
        for k in self._workflows:
            self._workflows[k]["is_active"] = (k == version_id)
            self._workflows[k]["traffic_percentage"] = 100 if k == version_id else 0
        self._active_version = version_id
        self._save()
        return self._workflows[version_id]

    def rollback(self, target_version_id: str) -> Dict[str, Any]:
        return self.activate_version(target_version_id)
