"""
Usage Metering & Operational Telemetry for AI Plane.
Emits usage-metering events with tenant, workflow version, model name, tokens, and latency.
Publishes to Kafka topic usage.events.v1 and local structured logs.
"""
import json
import logging
import os
import time
from typing import Dict, Any, Optional
from datetime import datetime, timezone

logger = logging.getLogger(__name__)

# In-memory aggregate telemetry store
_METRICS_STORE: Dict[str, Any] = {
    "total_invocations": 0,
    "total_tokens": 0,
    "total_latency_ms": 0.0,
    "by_tenant": {},
    "by_workflow_version": {},
    "recent_events": [],
}

def emit_usage_event(
    tenant_id: str,
    workflow_version: str,
    tokens_used: int,
    latency_ms: float,
    model_name: str = "mock-llama3-8b",
    outcome: str = "SUCCESS",
    ticket_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Records and emits a usage metering event conforming to contracts/events."""
    event = {
        "eventId": f"use-{int(time.time() * 1000)}",
        "tenantId": tenant_id,
        "workflowVersion": workflow_version,
        "modelName": model_name,
        "ticketId": ticket_id,
        "tokensUsed": tokens_used,
        "latencyMs": round(latency_ms, 2),
        "outcome": outcome,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    # 1. Update in-memory telemetry
    _METRICS_STORE["total_invocations"] += 1
    _METRICS_STORE["total_tokens"] += tokens_used
    _METRICS_STORE["total_latency_ms"] += latency_ms

    tenant_rec = _METRICS_STORE["by_tenant"].setdefault(
        tenant_id, {"invocations": 0, "tokens": 0, "latency_ms": 0.0}
    )
    tenant_rec["invocations"] += 1
    tenant_rec["tokens"] += tokens_used
    tenant_rec["latency_ms"] += latency_ms

    wf_rec = _METRICS_STORE["by_workflow_version"].setdefault(
        workflow_version, {"invocations": 0, "tokens": 0}
    )
    wf_rec["invocations"] += 1
    wf_rec["tokens"] += tokens_used

    _METRICS_STORE["recent_events"].append(event)
    if len(_METRICS_STORE["recent_events"]) > 50:
        _METRICS_STORE["recent_events"].pop(0)

    # 2. Log structured metric event
    logger.info("ai_usage_event", extra={"usage_event": event})

    # 3. Optional Kafka publish if kafka library available
    try:
        from kafka import KafkaProducer
        kafka_bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            request_timeout_ms=1000,
        )
        producer.send("usage.events.v1", key=tenant_id.encode("utf-8"), value=event)
        producer.flush(timeout=1.0)
    except Exception:
        # Graceful degradation if Kafka is unreachable
        pass

    return event

def get_metrics_summary() -> Dict[str, Any]:
    """Returns platform-wide AI usage metrics summary."""
    n = _METRICS_STORE["total_invocations"]
    avg_latency = (_METRICS_STORE["total_latency_ms"] / n) if n > 0 else 0.0
    return {
        "totalInvocations": n,
        "totalTokens": _METRICS_STORE["total_tokens"],
        "avgLatencyMs": round(avg_latency, 2),
        "byTenant": _METRICS_STORE["by_tenant"],
        "byWorkflowVersion": _METRICS_STORE["by_workflow_version"],
        "recentEventsCount": len(_METRICS_STORE["recent_events"]),
    }
