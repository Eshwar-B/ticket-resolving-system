# Agentic Engineering Guidelines and Operating Standards (AGENT.md)

> **Platform:** Enterprise AI Platform (Multi-Tenant, Event-Driven AI Support, Knowledge & Workflow Automation)  
> **Executive Mandate:** Strictly follow [`guidlines.md`](./guidlines.md)  
> **Architectural Specification:** [`ENTERPRISE_AI_PLATFORM_BLUEPRINT.md`](./ENTERPRISE_AI_PLATFORM_BLUEPRINT.md)  
> **Core Philosophy:** **BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT**

---

## 1. Role and Agent Team Structure

You operate as an integrated Autonomous Development Team utilizing Antigravity's capabilities to their full potential:

| Agent Role | Primary Focus & Domain |
|---|---|
| **Principal Architect** | System design coherence, ADR enforcement, cross-service boundary governance, trade-off resolution |
| **Java Backend Engineer** | Spring Boot 3 control plane, outbox publisher, state machines, JPA/Flyway migrations, Resilience4j |
| **Python AI Engineer** | LangGraph agent graphs, hybrid RAG pipeline, Qdrant integration, Ollama/mock-LLM, evaluation gates |
| **Database Engineer** | PostgreSQL RLS policies, indexing strategy, transaction isolation, Redis caching & namespaces |
| **DevOps Engineer** | Docker Compose profiles, memory limits (16 GB cap), OTel collector, Prometheus/Grafana, K8s, CI/CD |
| **Security Engineer** | Keycloak OIDC, JWT tenant context, prompt injection defense, PII redaction, STRIDE threat model |
| **QA & Test Engineer** | Testcontainers, tenant isolation negative test suite, chaos drills, k6 load tests against mock LLM |
| **Documentation & Reverse-Engineering** | Reverse-engineering guides (`docs/reverse-engineering/`), Mermaid diagrams, runbooks |

---

## 2. Mandatory 10-Step Phase Workflow

For **EVERY** phase and feature, strictly execute this cycle:

1. **Analyze:** Read relevant blueprint sections, architectural constraints, and existing implementation.
2. **Plan:** Define architecture, components, dependencies, API contracts, data models, risks, and acceptance criteria. Update `docs/agent/IMPLEMENTATION_PLAN.md`.
3. **Delegate:** Formulate sub-tasks mapped to specialized agent roles without overlapping file write conflicts.
4. **Implement:** Write production-grade application code, configurations, infrastructure, migrations, and documentation.
5. **Integrate:** Wire services across REST/Kafka boundaries and confirm contract alignment.
6. **Test:** Execute unit tests, Testcontainers integration tests, tenant isolation tests, and failure injection.
7. **Review:** Conduct automated code, security, and architectural review against the Blueprint.
8. **Fix:** Resolve any identified defects, lint warnings, or security risks.
9. **Document:** Write technical documentation and **Reverse-Engineering Guides** in `docs/reverse-engineering/`.
10. **Close the Phase:** Update `docs/agent/PROJECT_STATE.md`, `docs/agent/TASK_LOG.md`, and generate a completion report.

---

## 3. Mandatory State Management Tracking

The agent team must maintain these six living files in `docs/agent/`:

1. `docs/agent/PROJECT_STATE.md`: Active phase, completed milestones, environment status, component readiness.
2. `docs/agent/IMPLEMENTATION_PLAN.md`: Active phase breakdown, atomic tasks, owners, dependencies, and acceptance criteria.
3. `docs/agent/TASK_LOG.md`: Chronological audit log of completed actions, file modifications, and outcomes.
4. `docs/agent/TEST_STATUS.md`: Test suite matrix, coverage status, and tenant isolation test results.
5. `docs/agent/BLOCKERS.md`: Known bugs, environment hurdles, trade-offs, and open architectural assumptions.
6. `docs/agent/AGENT_WORKFLOW.md`: Registered Antigravity workflows, agent roles, and orchestration procedures.

---

## 4. Reverse-Engineering Documentation Standard

Every implemented feature must produce a detailed document in `docs/reverse-engineering/` organized by phase and feature, answering:

1. What business problem does this feature solve?
2. Why was this specific architecture chosen over alternatives?
3. How does the implementation work internally (step-by-step)?
4. Which design patterns are applied (Outbox, Inbox, Strategy, Saga, Interceptor)?
5. What happens during a successful request/event?
6. What happens during failure (network drop, poison message, DB lock)?
7. How does data flow between components (with Mermaid sequence/data-flow diagrams)?
8. What security and tenant isolation considerations exist?
9. Which specific classes, methods, configurations, and SQL queries should be inspected first?
10. How can the user reproduce, test, and debug this behavior locally?

---

## 5. Non-Negotiable Engineering Rules

1. **Follow the Blueprint:** Any change to architecture, DB, or tech stack requires an ADR in `docs/adr/`.
2. **Phase-by-Phase Delivery:** Build bounded context by bounded context. Never generate massive unverified codebases in one shot.
3. **Cost = ₹0 (Zero Paid Services):** Run 100% locally with open-source tools (Postgres, Redis, Kafka KRaft, Keycloak, Qdrant, MinIO, Ollama, OTel). Keep external integrations behind interfaces.
4. **Strict Multi-Tenancy:**
   - Server-side tenant derivation from validated Keycloak JWTs only.
   - Transaction-scoped PostgreSQL Row-Level Security (`SET LOCAL app.tenant_id = ...`).
   - Non-superuser `app_user` role.
   - Redis keys scoped with `t:{tenant_id}:...`.
   - Kafka topics partitioned by `tenantId`.
   - Mandatory `tenant_id` payload filter on all Qdrant vector queries.
   - Automated tenant isolation negative test suite.
5. **No Cross-Database Writes:** Services own their logical databases exclusively. No shared tables or JPA cross-mapping.
6. **Canonical Event Envelope:** All Kafka messages conform to `contracts/events/envelope.v1.json`.
7. **Effectively-Once Processing:**
   - Transactional Outbox pattern on all domain writes.
   - Idempotent inbox table (`inbox_event`) on all consumers.
   - `Idempotency-Key` headers on mutation APIs and side effects.
   - Retry topics with exponential backoff + jitter and DLQ replay tooling.
8. **Graceful Degradation:** Core ticketing operations must continue when Ollama or Qdrant is down.
9. **Observability & Zero Secret Leaks:** W3C `traceparent` propagation across HTTP and Kafka headers. Structured JSON logs with `trace_id` and `tenant_id`. No secrets or unredacted PII in logs.
10. **Hardware Discipline (16 GB RAM):** Cap JVM heaps (`-Xmx256m` to `-Xmx384m`). Set Docker container limits. Use Compose profiles (`core`, `ai`, `obs`, `tools`). Never run `full` Compose alongside kind.

---

## 6. Autonomy and Safety Boundaries

### Safely Automated (Proceed Autonomously)
- Creating and editing source files, scripts, configurations, and documentation.
- Writing and executing unit, integration, and security tests.
- Building and debugging applications locally.
- Creating and applying Flyway database migrations.
- Running non-destructive Docker commands (`docker compose up`, `ps`, `logs`).

### Requires User Approval
- Deleting persistent data or pruning Docker volumes (`docker compose down -v`).
- Resetting or dropping live databases.
- Destructive Git operations (e.g., hard reset, force push).
- Installing global system packages.
- Committing to remote VCS or deploying to cloud.
- Any action incurring financial cost.
