"""Enterprise AI Platform: Python AI Plane Structured Logging Foundation.

Conforms to Blueprint Section 14: Structured JSON logging with trace_id, span_id,
tenant_id, and W3C context propagation.
"""

import logging
import sys
from typing import Any, Dict
import structlog
from opentelemetry import trace


def add_opentelemetry_context(
    logger: logging.Logger, method_name: str, event_dict: Dict[str, Any]
) -> Dict[str, Any]:
    """Extract active OpenTelemetry trace and span IDs and inject into log records."""
    span = trace.get_current_span()
    span_context = span.get_span_context() if span else None

    if span_context and span_context.is_valid:
        event_dict["trace_id"] = format(span_context.trace_id, "032x")
        event_dict["span_id"] = format(span_context.span_id, "016x")
    else:
        event_dict.setdefault("trace_id", "none")
        event_dict.setdefault("span_id", "none")

    return event_dict


def configure_structured_logging(service_name: str = "ai-plane", log_level: str = "INFO") -> None:
    """Configure structlog to emit JSON logs containing standard enterprise attributes."""
    shared_processors = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.processors.TimeStamper(fmt="iso", utc=True),
        add_opentelemetry_context,
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
    ]

    structlog.configure(
        processors=shared_processors + [structlog.processors.JSONRenderer()],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )

    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=getattr(logging, log_level.upper(), logging.INFO),
    )


if __name__ == "__main__":
    configure_structured_logging()
    logger = structlog.get_logger("foundation-test")
    logger.info("Structured logging foundation initialized successfully", tenant_id="tenant-demo")

