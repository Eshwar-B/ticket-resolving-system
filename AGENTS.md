# Enterprise AI Platform — Coding Agent Instructions (AGENTS.md)

Welcome to the **Enterprise AI Platform** repository. All automated coding agents, subagents, and human developers must follow the authoritative governance documents provided in the root:

1. **Architectural Specification & Source of Truth:** [`ENTERPRISE_AI_PLATFORM_BLUEPRINT.md`](./ENTERPRISE_AI_PLATFORM_BLUEPRINT.md)
2. **Autonomous Operating Standards & Rules:** [`AGENT.md`](./AGENT.md)
3. **Mission, Antigravity Utilization & Learning Philosophy:** [`guidlines.md`](./guidlines.md)
4. **Active Phased Execution Plan:** [`docs/agent/IMPLEMENTATION_PLAN.md`](./docs/agent/IMPLEMENTATION_PLAN.md) (and master [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md))

---

## Non-Negotiable Core Rules

- **Build One Phase at a Time:** Do not prematurely implement future phase business logic. Follow the current active phase in `docs/agent/PROJECT_STATE.md`.
- **Cost = ₹0:** Everything must execute locally on Windows 11 with 16 GB RAM using open-source tooling (Docker Compose, PostgreSQL, Redis, Kafka KRaft, MinIO, Qdrant, Ollama, OpenTelemetry). No paid cloud APIs are permitted.
- **Hardware Discipline (16 GB RAM Cap):** Respect container memory caps and JVM limits (`-Xmx256m` to `-Xmx384m`). Only launch the required profile (`core`, `ai`, `obs`).
- **Strict Multi-Tenancy:** Server-side tenant derivation from validated Keycloak JWTs only. Row-Level Security with `SET LOCAL app.tenant_id = ...`. Tenant-scoped Redis keys (`t:{tenant_id}:...`) and Kafka partition keys.
- **Service Database Ownership:** Each service owns its dedicated database exclusively. No cross-database joins or cross-service schema writes.
- **Canonical Events & Reliability:** Transactional outbox pattern on mutation writes, idempotent consumer inboxes, DLQs with retry backoff.
- **Git Restrictions:** Do not initialize or execute Git commands until explicitly requested by the user.
- **Documentation Standard:** Maintain all tracking files in `docs/` and create comprehensive reverse-engineering guides in `docs/reverse-engineering/` for every completed feature.

