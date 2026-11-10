"""
Workflow Version Registry Facade.
Provides singleton access to workflow version management and Canary controls.
"""
from typing import List, Dict, Any, Optional
from app.workflow_versions.version_store import VersionStore

_store: Optional[VersionStore] = None

def get_version_store() -> VersionStore:
    global _store
    if _store is None:
        _store = VersionStore()
    return _store

def get_active_workflow_version() -> str:
    return get_version_store().get_active_version()

def list_workflow_versions() -> List[Dict[str, Any]]:
    return get_version_store().list_workflows()

def activate_workflow_version(version_id: str) -> Dict[str, Any]:
    return get_version_store().activate_version(version_id)

def rollback_workflow_version(target_version_id: str) -> Dict[str, Any]:
    return get_version_store().rollback(target_version_id)
