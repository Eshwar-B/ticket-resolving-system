# Enterprise AI Platform — Master Implementation Plan

**Status:** Proposed execution baseline  
**Architecture source:** `ENTERPRISE_AI_PLATFORM_BLUEPRINT.md` (Frozen baseline v1.0)  
**Engineering rules:** `AGENT.md`  
**Execution mandate:** `guidlines.md`  
**Target environment:** Windows + Docker Desktop/WSL2, 16 GB RAM  
**Cost constraint:** ₹0; local-first; open-source dependencies only  
**Development philosophy:** BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT

---

## 1. Purpose and authority of this plan

This file converts the three supplied project documents into an executable, phase-by-phase plan for Antigravity.

Use the documents in this order:

1. `ENTERPRISE_AI_PLATFORM_BLUEPRINT.md` is the architectural source of truth. Its technology choices and service boundaries are frozen baseline decisions. Any architectural or technology change requires an ADR.
2. `AGENT.md` defines the engineering team roles, mandatory ten-step workflow, state tracking, reverse-engineering documentation and safety boundaries.
3. `guidlines.md` defines the mission, Antigravity capability inspection, learning approach, autonomy expectations and final acceptance criteria.
4. `docs/agent/IMPLEMENTATION_PLAN.md` (this file, copied into the repository) is the active execution plan. Update its task status as work progresses; do not silently override the blueprint.

Do not generate the entire system in one prompt. Implement one phase and one bounded context at a time. A phase cannot close while critical exit criteria fail.

## 2. Product outcome

Build a local-first, multi-tenant, event-driven Enterprise AI Support, Knowledge and Workflow Automation Platform.

The system must allow multiple organisations to manage customers, support tickets, knowledge documents, AI-assisted resolutions, workflows, approvals, notifications, usage and audit data while enforcing tenant isolation.

The principal demonstration scenario is:

A customer reports that an order was delivered with an empty package and requests a refund. The platform creates the ticket synchronously, publishes an event through an outbox, triages it asynchronously, retrieves the correct tenant's refund policy, drafts a cited response, checks refund eligibility through an allow-listed tool, requests human approval, executes the approved action idempotently, updates the ticket and records traces, usage and audit evidence.

**Core business operations must remain usable when Ollama or Qdrant is unavailable.** Ticket creation must not depend on successful AI inference.

## 3. Fixed architectural baseline

### 3.1 Application planes

- **Java control plane:** Java 21, Spring Boot 3.x, Spring Security, Spring Cloud Gateway, JPA/Hibernate, Flyway, Spring Kafka and Resilience4j.
- **Python AI plane:** Python 3.12, FastAPI, Pydantic v2, LangGraph, Ollama/model adapters, RAG, guardrails and evaluation.
- **Shared infrastructure:** PostgreSQL 16, Redis, Kafka in KRaft mode, Keycloak, Qdrant, MinIO and OpenTelemetry-based observability.

### 3.2 Java services

Start with six logical domain services plus the gateway. Do not split further without measured need and an ADR.

| Component | Ownership |
|---|---|
| Gateway | Authentication enforcement, routing, tenant resolution, rate limiting; no domain DB |
| Identity/Tenant | Tenants, memberships, roles, teams, quotas, API keys; `identity_db` |
| Ticket | Tickets, comments, assignments, history, SLA, outbox; `ticket_db` |
| Knowledge | Document metadata, versions, permissions, ingestion status; `knowledge_db` |
| Workflow | Workflow definitions/executions, approvals, saga state; `workflow_db` |
| Notification | In-app notifications, webhook subscriptions and delivery state; `notification_db` |
| Usage/Audit | Metering, quotas, audit trail and cost estimates; `usage_db` |

### 3.3 Python AI plane

Begin as one Python application with modular packages and separate worker processes where useful. Do not prematurely create many network services.

Modules: `ai_api`, `event_consumer`, `supervisor`, `agents`, `tools`, `rag`, `memory`, `guardrails`, `context`, `model_adapter`, `evaluation`, `workflow_versions` and `observability`.

The AI plane may own `ai_db` for AI-specific state, checkpoints, memory metadata, chunks/FTS, evaluation runs and workflow versions. It must not become a second source of truth for tickets or tenants.

### 3.4 Non-negotiable engineering invariants

1. Tenant identity comes only from a validated Keycloak JWT; never trust a client-supplied tenant ID.
2. Apply PostgreSQL RLS to tenant-owned rows. Set `app.tenant_id` transaction-locally using `SET LOCAL`; use a non-superuser application DB role that does not bypass RLS.
3. Scope Redis keys as `t:{tenant_id}:...`.
4. Include `tenantId` in Kafka envelopes and partition keys.
5. Every Qdrant query must apply a mandatory `tenant_id` filter and document-permission filter.
6. Scope MinIO objects under `tenants/{tenantId}/...`.
7. Services own their data. No cross-service table writes, shared repositories or JPA cross-mapping.
8. All domain events use `contracts/events/envelope.v1.json` and versioned event schemas.
9. Use transactional outbox for producer-side domain writes; inbox/deduplication for consumers; `Idempotency-Key` for mutation APIs and side effects.
10. Implement retry with exponential backoff and jitter, retry budgets, DLQ inspection/replay and poison-message isolation.
11. All outbound calls need timeouts and appropriate retry/circuit-breaker/bulkhead behaviour.
12. Agents use only allow-listed tools with schema-validated arguments. No arbitrary SQL, shell or unrestricted network access. High-risk actions require human approval.
13. Never log secrets or unredacted PII. Propagate W3C `traceparent` across HTTP and Kafka.
14. All DB changes use Flyway migrations.
15. Keep core ticketing independent of AI availability.
16. No paid service or cloud account is required for the critical path.
17. Never claim a performance result without a reproducible test and recorded output.

## 4. Repository target

Use the blueprint's repository structure, creating directories only when a phase needs them:

```text
enterprise-ai-platform/
├── README.md
├── AGENTS.md
├── AGENT.md
├── guidlines.md
├── ENTERPRISE_AI_PLATFORM_BLUEPRINT.md
├── docker-compose.yml
├── .env.example
├── Makefile
├── apps/
│   └── admin-console/
├── backend/
│   ├── common/
│   ├── gateway/
│   ├── identity-tenant-service/
│   ├── ticket-service/
│   ├── knowledge-service/
│   ├── workflow-service/
│   ├── notification-service/
│   └── usage-audit-service/
├── ai-plane/
│   ├── app/
│   │   ├── api/
│   │   ├── consumers/
│   │   ├── supervisor/
│   │   ├── agents/
│   │   ├── tools/
│   │   ├── rag/
│   │   ├── memory/
│   │   ├── guardrails/
│   │   ├── context/
│   │   ├── model_adapter/
│   │   ├── evaluation/
│   │   ├── workflow_versions/
│   │   └── observability/
│   ├── mock-llm/
│   ├── tests/
│   └── pyproject.toml
├── contracts/
│   ├── events/
│   └── openapi/
├── infra/
│   ├── docker/
│   ├── observability/
│   ├── k8s/
│   ├── terraform/
│   └── keycloak/
├── testing/
│   ├── load/
│   ├── chaos/
│   ├── security/
│   └── ai-eval/
├── scripts/
├── docs/
│   ├── product/
│   ├── architecture/
│   ├── adr/
│   ├── database/
│   ├── messaging/
│   ├── ai/
│   ├── security/
│   ├── performance/
│   ├── operations/
│   ├── cost/
│   ├── reverse-engineering/
│   ├── interview/
│   └── agent/
└── .github/
    └── workflows/
```

**Instruction-file naming:** The blueprint explicitly recommends `AGENTS.md` for coding-agent instructions. Keep `AGENT.md` as the engineering operating standards file and `guidlines.md` as the mission/execution prompt. Place all three in the repository root and make `AGENTS.md` point to the other two and the blueprint. Preserve the spelling `guidlines.md` if that is the filename supplied to Antigravity; do not silently rename it.

## 5. Antigravity operating model

### 5.1 Capability discovery before delegation

At the start of Phase 0, inspect the installed Antigravity version and record what is actually available. Verify:

- Main coding agents and selectable models.
- Parallel agent execution and subagent support.
- Planning/task delegation.
- Terminal and browser interaction.
- MCP servers and integrations.
- Skills and reusable workflows.
- Project-level rules and instruction files.
- Hooks or lifecycle automation, if supported.
- Git/worktree support.
- Test/debug integration.
- Context and memory behaviour.
- Permission and approval controls.

Do not claim a capability exists until it has been observed in the installed environment. Record unavailable capabilities and use a simpler supported workflow.

### 5.2 Agent responsibilities

| Role | Responsibility | Write ownership |
|---|---|---|
| Principal Architect | Architecture, ADRs, service boundaries, integration decisions | ADRs, architecture docs, plan/state |
| Java Backend Engineer | Gateway and Spring Boot services, APIs, events, migrations | Assigned Java service only |
| Python AI Engineer | FastAPI, LangGraph, RAG, model adapters, evaluation | `ai-plane/` |
| Database Engineer | Schema design, RLS, indexes, migrations review | Assigned DB migrations/policies; coordinate with service owner |
| DevOps Engineer | Compose, Dockerfiles, CI, observability, kind, Terraform validation | `infra/`, CI and deployment files |
| Security Engineer | Threat model, auth review, tenant isolation, AI security | Security docs and assigned tests |
| QA Engineer | Unit/integration/E2E, contract, isolation, chaos and load tests | `testing/` and test files assigned by service |
| Documentation Engineer | Reverse-engineering guides, runbooks, diagrams, completion reports | `docs/` except active plan/state owned by architect |

The principal architect coordinates changes. Avoid concurrent writes to the same file. Parallelize only independent tasks, such as reviewing a threat model while another agent drafts an isolated service contract.

### 5.3 Mandatory ten-step workflow for every phase

1. Analyze the relevant blueprint, existing code and dependencies.
2. Plan architecture, files, APIs, schemas, risks, tests and acceptance criteria; update this plan.
3. Delegate bounded independent tasks and assign file ownership.
4. Implement code, configuration, migrations and docs.
5. Integrate REST/Kafka contracts and verify ownership boundaries.
6. Test unit, integration, tenant isolation, security and failure behaviour as relevant.
7. Review architecture, code quality, security and coverage independently.
8. Fix all critical defects and regressions.
9. Document implementation and reverse-engineering details.
10. Close phase by updating project state, task log, test status, blockers and completion report.

### 5.4 Antigravity workflows to configure where supported

- `phase-start`: read instructions, inspect state, summarize phase scope and risks.
- `plan-feature`: list files, ownership, contracts, migrations, tests and acceptance criteria before coding.
- `implement-feature`: implement only approved phase tasks.
- `verify-feature`: run relevant tests, lint, security checks and failure tests.
- `review-feature`: independent architecture/security/code review.
- `document-feature`: update technical and reverse-engineering docs.
- `close-phase`: verify exit criteria and update state/log/report.

If workflows, hooks or subagents are unavailable, execute the same steps manually with explicit prompts. Never invent unavailable Antigravity features.

## 6. Phase roadmap and execution tasks

No fixed calendar deadline. Progress is milestone-based. Security, testing and observability are continuous responsibilities.

---

## Phase 0 — Requirements, architecture and agent setup

**Goal:** Freeze an understandable, testable architecture before application implementation.

**Tasks**
1. Inspect the repository. Identify whether it is empty, partially scaffolded or already contains code. Do not overwrite existing work.
2. Inspect Antigravity capabilities and record verified features, unavailable features and permission boundaries.
3. Place the three source documents in the repository root with the exact agreed names.
4. Create `AGENTS.md` as the concise entry point for coding agents, referencing the blueprint, `AGENT.md` and `guidlines.md`.
5. Create product documents:
   - `docs/product/product-requirements.md`
   - `docs/product/functional-requirements.md`
   - `docs/product/non-functional-requirements.md`
   - `docs/product/use-cases.md`
6. Create architecture documents:
   - `docs/architecture/system-context.md`
   - `docs/architecture/hld.md`
   - `docs/architecture/lld.md` (initial outline)
   - `docs/architecture/service-boundaries.md`
   - `docs/architecture/data-flow.md`
   - `docs/architecture/deployment-architecture.md` (initial outline)
7. Create initial ADRs 001–011 as described in the blueprint. Each ADR must contain context, decision, alternatives, trade-offs and consequences. Mark unresolved decisions as proposed rather than pretending they are final.
8. Draft `docs/security/threat-model.md` with trust boundaries and initial STRIDE risks.
9. Define API boundaries, service ownership, logical DB ownership and initial OpenAPI contracts.
10. Define the event catalogue and standard event envelope at `contracts/events/envelope.v1.json`; create schemas for initial events.
11. Create initial Mermaid system-context, container, ticket lifecycle, refund approval and event-flow diagrams.
12. Create the six `docs/agent/` tracking files.
13. Create `docs/reverse-engineering/README.md` explaining the documentation convention.
14. Review the complete Phase 0 output with independent architecture and security reviews.

**Exit criteria**
- Product scope, non-goals and end-to-end scenario are explicit.
- Every service has a reason to exist and a clear owner.
- Sync/async boundaries and event contracts are documented.
- Tenant trust boundaries and initial threats are documented.
- ADRs explain important decisions.
- The developer can explain the architecture and principal failure scenarios.
- No application implementation is started before this design baseline is reviewed.

**Do not build:** Full backend services, full AI pipeline, React UI, Kubernetes or cloud infrastructure.

---

## Phase 1 — Monorepo foundation and local infrastructure

**Goal:** Establish reproducible local development and observability foundations.

**Tasks**
1. Create the monorepo layout and root README.
2. Add pinned dependency versions, formatting/lint rules and `.gitignore`.
3. Create `.env.example`; keep actual `.env` ignored and never commit secrets.
4. Create Docker Compose profiles:
   - `core`: PostgreSQL, Redis, Kafka KRaft, Keycloak, MinIO and gateway/domain services as they become available.
   - `ai`: FastAPI, workers, Ollama and Qdrant.
   - `obs`: OTel Collector, Prometheus, Grafana, Loki, Tempo and Phoenix.
   - `tools`: Mailpit, Kafka UI, Toxiproxy and optional tools.
   - `full`: occasional integration profile only.
5. Initially bring up only infrastructure dependencies required for the foundation; do not create empty domain services merely to populate Compose.
6. Configure PostgreSQL with separate logical databases per service and least-privilege roles.
7. Configure Redis and Kafka KRaft with persistent local volumes and health checks.
8. Configure Keycloak and MinIO with local-only development credentials from environment variables.
9. Add Flyway baseline conventions and migration examples.
10. Add Java and Python build/test skeletons.
11. Add GitHub Actions for build, unit tests, lint and secret scanning.
12. Add OpenTelemetry SDK/agent and structured JSON logging skeletons from the start.
13. Propagate trace context across a sample HTTP request; extend Kafka propagation when the first event flow is implemented.
14. Add Makefile or equivalent scripts for common tasks: `up-core`, `up-ai`, `up-obs`, `test`, `lint`, `logs`, `down`.
15. Measure resource usage with `docker stats` and record baseline.

**Exit criteria**
- A documented command starts the required foundation profile.
- Containers pass health checks and restart predictably.
- Java and Python skeletons build and tests run in CI.
- No secrets are committed.
- Logs and a sample trace are visible.
- Actual memory usage is measured and within the machine budget.

---

## Phase 2 — Identity, authentication and multi-tenancy

**Goal:** Prove tenant isolation before building tenant-owned business features.

**Tasks**
1. Configure Keycloak realm, clients, roles and development users for at least two tenants.
2. Implement Gateway JWT validation and server-side tenant resolution.
3. Implement Identity/Tenant APIs for tenant registration, membership, roles, teams and configuration.
4. Implement RBAC and selected resource-level authorization.
5. Create `identity_db` migrations and tenant ownership constraints.
6. Implement tenant context propagation to downstream services.
7. Implement transaction-scoped PostgreSQL RLS using `SET LOCAL app.tenant_id`.
8. Ensure the application DB role is not a superuser or table owner and cannot bypass RLS.
9. Add tenant-scoped Redis key helpers and tests.
10. Add negative tests for forged tenant IDs, cross-tenant reads/writes, missing tenant context, pooled-connection leakage, invalid/expired JWTs and 401/403 behaviour.
11. Add audit events for identity and permission changes.
12. Document JWT claims, trust boundaries, RLS policies and test setup.

**Exit criteria**
- Two tenants can authenticate and use the service.
- Tenant A cannot read or modify Tenant B's data.
- Client-supplied tenant IDs cannot override the validated JWT context.
- RLS and connection-pool isolation tests pass.

---

## Phase 3 — Core ticketing (without AI)

**Goal:** Deliver a usable support workflow that does not depend on the AI plane.

**Tasks**
1. Implement Ticket Service with its own `ticket_db`.
2. Define customer, ticket, comment, assignment, history and SLA entities.
3. Add Flyway migrations, constraints and indexes.
4. Implement ticket CRUD and `/api/v1/...` REST APIs.
5. Enforce the ticket state machine:
   `NEW -> OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED`,
   with `WAITING_FOR_CUSTOMER`, `WAITING_FOR_APPROVAL` and permitted reopen transitions.
6. Reject arbitrary status changes; enforce valid transitions in domain logic.
7. Implement assignment, comments, history and SLA metadata.
8. Add cursor-based pagination for large ticket lists and allow-listed sorting/filtering.
9. Add RFC 7807 `problem+json` error responses and correlation IDs.
10. Add OpenAPI documentation and contract tests.
11. Add tenant isolation tests for every ticket endpoint.
12. Add reverse-engineering guide with request lifecycle, state machine and key classes.

**Exit criteria**
- A support user can create, assign, comment on, transition and resolve a ticket.
- Ticketing works with Ollama, Qdrant and the AI plane stopped.
- Tenant isolation and API tests pass.

---

## Phase 4 — Event-driven reliability and distributed systems

**Goal:** Make asynchronous workflows reliable under duplicates, retries, crashes and dependency failures.

**Tasks**
1. Finalize the canonical event envelope and JSON schemas.
2. Implement transactional outbox in Ticket Service: ticket mutation and outbox insert commit in one DB transaction.
3. Implement outbox publisher with safe polling/locking and trace propagation.
4. Implement consumer inbox/deduplication for each consumer.
5. Add `Idempotency-Key` support to mutation APIs and store request hash/result safely.
6. Configure Kafka topic naming, partitioning by `tenantId`, retention and consumer groups.
7. Implement retry topics with exponential backoff, jitter and retry budgets.
8. Implement DLQ routing, inspection and operator-controlled replay tooling.
9. Add event version/sequence checks for out-of-order updates.
10. Add Resilience4j timeouts, retries, circuit breakers and bulkheads to outbound calls.
11. Implement Workflow Service saga primitives where required by the refund flow.
12. Add Toxiproxy and container-restart drills.
13. Test duplicate delivery, consumer crash, broker outage, poison message, out-of-order event and recovery.
14. Document event contracts, outbox/inbox internals, replay procedure and failure semantics.

**Exit criteria**
- Duplicate event delivery does not duplicate business effects.
- A crash/restart recovers processing safely.
- Poison messages reach the DLQ without stopping healthy messages.
- Broker downtime does not corrupt committed ticket state.
- Retry and replay behaviour is tested and documented.

---

## Phase 5 — Knowledge management and RAG

**Goal:** Ingest tenant-specific knowledge and return permission-filtered, cited retrieval results.

**Tasks**
1. Implement Knowledge Service document metadata, versions, permissions and ingestion status.
2. Store uploaded objects in MinIO under tenant-specific prefixes.
3. Validate file type, size and content; implement safe extraction and malware-safe handling.
4. Support initial PDF and text ingestion; add URL ingestion only with SSRF protections.
5. Build Python ingestion pipeline: extract, clean, chunk, attach metadata and compute content hashes.
6. Implement embeddings using the model adapter and selected local embedding model.
7. Store vectors in Qdrant with mandatory `tenant_id`, document ID, version and permission metadata.
8. Store chunk text/FTS metadata in `ai_db` using PostgreSQL full-text search.
9. Implement hybrid retrieval using PostgreSQL FTS + Qdrant and Reciprocal Rank Fusion.
10. Add cross-encoder reranking where local resource budget permits.
11. Enforce tenant and document permission filters before returning context.
12. Implement citations with document/version/page/chunk references and citation verification.
13. Propagate document update/delete events and reconcile stale vectors.
14. Build a golden retrieval dataset and benchmark chunk sizes, embedding options, hybrid/vector-only retrieval and reranking.
15. Add tests for cross-tenant retrieval, revoked permissions, document deletion and stale versions.

**Exit criteria**
- A tenant can upload and index a document.
- Queries return relevant chunks with traceable citations.
- Unauthorized tenant/document content is never returned.
- Retrieval benchmark results are recorded; no quality claim is made without evidence.

---

## Phase 6 — Agentic AI and human approvals

**Goal:** Process support tickets through a controlled, recoverable LangGraph workflow.

**Tasks**
1. Implement the AI event consumer with inbox/idempotency.
2. Implement LangGraph Supervisor with iteration, timeout and token budgets.
3. Implement Triage, Retrieval, Resolution, Action/Tool, Critic/Verifier, Escalation and Summarisation agents.
4. Implement context assembly in the documented order: system policy, tenant config, customer profile, retrieved documents, conversation summary and current task.
5. Implement allow-listed tools with typed schemas:
   `get_ticket_details`, `search_customer_orders`, `check_refund_eligibility`, `get_shipping_status`, `search_knowledge_base`, `update_ticket_status`, `create_internal_note`, `request_human_approval`, `send_customer_notification`.
6. Use simulated order/refund/shipping backends; do not integrate real payments or email.
7. Enforce the permission matrix: reading and eligibility checks may be allowed; refund execution requires human approval; tenant settings and arbitrary shell/SQL are forbidden.
8. Implement Workflow Service approval request/approve/reject APIs and state transitions.
9. Implement LangGraph checkpoint persistence and resume after worker crash.
10. Implement short-term conversation memory in Redis and durable/semantic memory as specified in the blueprint.
11. Implement memory expiry, tenant boundaries and forget-me/delete flow.
12. Add cancellation, escalation and human handoff paths.
13. Test prompt injection attempts, unauthorized tool calls, invalid arguments, approval bypass and crash recovery.
14. Document agent graph, tool permissions, memory lifecycle and successful/failure paths.

**Exit criteria**
- A ticket triggers triage, retrieval and a grounded draft.
- The AI can call only authorized tools.
- High-risk refund execution cannot happen without approval.
- A worker crash resumes from a checkpoint without duplicate side effects.
- Ticket creation remains available when the LLM is unavailable.

---

## Phase 7 — AI platform governance, evaluation and versioning

**Goal:** Make AI behaviour measurable, versioned, testable and reversible.

**Tasks**
1. Define model adapter interface: `generate`, `generate_structured`, `embed`, `health_check`.
2. Implement Ollama and configurable mock adapters; keep optional model gateways disabled by default.
3. Benchmark one generation model and one embedding model first; do not load multiple models unnecessarily.
4. Implement model routing by task only when benchmarks justify it.
5. Implement fallback, timeout and graceful degradation behaviour.
6. Version prompts, tools, model configurations, guardrails and LangGraph workflows.
7. Store evaluation runs and deployment state.
8. Create golden ticket datasets with expected category, citations, tool calls and approval requirements.
9. Measure groundedness, relevance, retrieval precision/recall, tool-call accuracy, task completion, hallucination rate, latency and token usage.
10. Integrate Ragas/custom evaluation and Arize Phoenix tracing.
11. Add CI regression thresholds for prompt/model/workflow changes.
12. Implement canary/A-B workflow comparison and rollback.
13. Emit usage-metering events with tenant and workflow/model version metadata.
14. Implement semantic caching, context compression and token budgets where measurement supports them.

**Exit criteria**
- At least two workflow versions can be compared using the same evaluation set.
- A workflow can be promoted or rolled back based on defined thresholds.
- AI quality and token/latency metrics are reproducible.
- Mock LLM is available for deterministic load testing.

---

## Phase 8 — Product completion and minimal admin experience

**Goal:** Connect the core domains into coherent tenant and operator workflows.

**Tasks**
1. Complete workflow definitions, triggers, conditions, executions and approvals.
2. Implement SLA breach checks and stale-ticket escalation using Quartz + ShedLock.
3. Complete Notification Service for in-app notifications and signed webhook delivery.
4. Implement webhook HMAC signatures, timestamps, replay protection, retries and delivery logs.
5. Implement developer API keys, hashed at rest and scoped by permission.
6. Implement per-user, per-tenant, per-key and per-endpoint rate limits using Redis.
7. Complete Usage/Audit Service and append-only audit events.
8. Implement quotas, usage aggregation and cost estimates.
9. Build a minimal React + TypeScript admin console only for essential flows: tenant/user administration, tickets, knowledge documents, approvals, workflow status and usage.
10. Add Playwright tests for critical user flows.
11. Keep GraphQL and a complex frontend deferred unless a concrete need is demonstrated.

**Exit criteria**
- Major support, admin, approval, knowledge and usage flows work end-to-end.
- Notifications/webhooks are observable and retryable.
- API key permissions and rate limits are enforced.
- Minimal UI is functional without taking priority over backend correctness.

---

## Phase 9 — Security hardening

**Goal:** Validate the security model across application, infrastructure, data and AI boundaries.

**Tasks**
1. Finalize STRIDE threat model and map OWASP Top 10 and OWASP LLM Top 10 risks.
2. Run the complete tenant isolation suite across Postgres, Redis, Kafka, Qdrant and MinIO.
3. Test forged/expired JWTs, authorization failures and privilege escalation.
4. Test SQL injection, malicious uploads, content sniffing and SSRF in URL ingestion.
5. Test webhook signature tampering and replay.
6. Test prompt injection (direct and indirect), cross-tenant retrieval, malicious tool arguments and unauthorized tool calls.
7. Verify PII redaction and secret-free logs.
8. Add Trivy, gitleaks, dependency scanning, OWASP ZAP baseline and SBOM generation to CI.
9. Use non-root containers, least privilege and read-only filesystems where practical.
10. Record findings, severity, remediation and residual risk.

**Exit criteria**
- Critical/high findings are fixed or explicitly risk-accepted with rationale.
- Tenant isolation and AI red-team suites pass.
- Security test report contains actual results and tool versions.

---

## Phase 10 — Observability and operations

**Goal:** Diagnose a ticket or failure end-to-end from a single trace and operate the system through documented procedures.

**Tasks**
1. Complete W3C trace propagation across Gateway → Java services → Kafka → Python workers → model/retrieval/tool calls.
2. Standardize JSON logs with `trace_id`, `span_id`, `tenant_id`, `ticket_id`, `correlation_id`, `agent` and `workflow_version`.
3. Add Prometheus metrics for request rate/latency/errors, Kafka lag, DLQ depth, retries, outbox backlog, DB pools, cache hit ratio, LLM latency/tokens and agent outcomes.
4. Configure Grafana dashboards for service health, Kafka, AI quality/usage, tenant usage and SLOs.
5. Configure alerts for consumer lag, DLQ growth, p95 latency, error rate, outbox age and SLO burn.
6. Use Phoenix for LLM traces, prompts, retrieved chunks, tool calls and evaluation results.
7. Write runbooks for wrong answers, Kafka lag, DLQ growth, database issues, Ollama outage and tenant-isolation alerts.
8. Define SLIs/SLOs and error budgets; measure AI availability separately from ticketing availability.
9. Run at least two incident drills and write postmortems.
10. Verify runbooks during drills and record verification dates.

**Exit criteria**
- A failed or incorrect ticket can be diagnosed through one correlated trace.
- Dashboards, alerts and runbooks are functional.
- Incident drills and postmortems are recorded.

---

## Phase 11 — Kubernetes and cloud-ready DevOps (local only)

**Goal:** Demonstrate deployability and orchestration without deploying to paid cloud infrastructure.

**Tasks**
1. Create kind cluster configuration and resource limits.
2. Create Helm charts or Kustomize overlays for local/staging/prod configuration.
3. Add readiness, liveness and startup probes.
4. Add ConfigMaps, Kubernetes Secrets for local demonstration and non-root security settings.
5. Add HPA and KEDA scaling AI workers based on Kafka consumer lag.
6. Add PodDisruptionBudgets and NetworkPolicies (default deny, explicit allow).
7. Demonstrate rolling updates, restart recovery and worker scaling.
8. Create Terraform reference modules for AWS architecture.
9. Run `terraform fmt`, `validate`, `tflint`, `tfsec`/`checkov` and Infracost where available.
10. Never run `terraform apply` or deploy to a real cloud under this project plan.
11. Document explicitly: **"Terraform was validated and cost-estimated, not applied to a real cloud."**

**Exit criteria**
- Local kind deployment starts, scales and recovers.
- Helm/Kustomize and Terraform validation pass.
- No real cloud deployment or spend occurs.

---

## Phase 12 — Advanced experiments, performance and disaster recovery

**Goal:** Produce measured engineering evidence and defend architecture trade-offs.

**Tasks**
1. Run k6 platform load tests against the mock LLM with controlled latency/token/failure behaviour.
2. Record tickets/sec, p50/p95/p99, errors, DB connections, Kafka lag/recovery, retries, DLQ growth and backpressure.
3. Benchmark real-model quality separately at low volume.
4. Generate 10k, 100k, 1M and (where hardware permits) 10M ticket datasets.
5. Compare query plans and performance for composite/partial/GIN indexes, FTS, pagination and partitioning using `EXPLAIN (ANALYZE, BUFFERS)`.
6. Benchmark Kafka partition counts, consumer scaling, rebalancing and hot-tenant skew.
7. Model 1k, 10k and 100k tickets/day and identify bottlenecks and capacity requirements.
8. Measure semantic cache hit rate, routing, context compression and batching impact.
9. Perform Postgres backup/restore, Qdrant snapshot and MinIO recovery drills.
10. Define and measure RPO/RTO.
11. Compare Temporal only after the Java workflow state machine is working; document an ADR and do not allow Temporal and LangGraph to compete for the same responsibility.
12. Evaluate read replicas, partitioning, sharding and hot-tenant strategies as experiments, not default implementation.
13. Record raw test outputs and reproducible commands in `testing/` and results in `docs/performance/`.

**Exit criteria**
- Performance claims are backed by scripts and raw results.
- Mock-LLM throughput is clearly distinguished from real LLM inference capacity.
- Database/Kafka bottlenecks and recovery behaviour are documented.
- Backup/restore drills have measured results.

---

## Phase 13 — Portfolio, final verification and handover

**Goal:** Make the project reproducible, explainable and demonstrable.

**Tasks**
1. Review every document against the implementation.
2. Complete architecture, ER, sequence, event-flow and deployment diagrams.
3. Complete reverse-engineering guides for every major feature.
4. Complete setup, troubleshooting and operations runbooks.
5. Prepare a reproducible demo using the refund scenario.
6. Record a demo video showing tenant isolation, ticket creation, RAG citations, agent tool use, human approval, event recovery and observability.
7. Prepare interview question bank and design defence notes.
8. Prepare a measured performance, AI evaluation and security evidence report.
9. Review README and ensure a new developer can run the project from a clean checkout.
10. Verify all final claims against test evidence; state limitations honestly.
11. Tag the v1 completion state only after all mandatory acceptance criteria pass.

**Exit criteria**
- A new developer can clone, configure and run the project using documented steps.
- The main demo is reproducible.
- Architecture, reliability, security and AI decisions can be defended with code and evidence.
- No unsupported production, cloud or performance claims remain.

---

## 7. Cross-phase quality gates

These checks apply in every phase, not only at the end.

### Every feature must have
- A documented requirement and design.
- A list of files created/modified.
- Unit tests.
- Integration tests where applicable.
- Tenant-isolation and security impact review.
- Failure behaviour tests.
- Structured logs, metrics and traces appropriate to the feature.
- Updated OpenAPI/event schemas.
- Updated reverse-engineering documentation.
- A successful relevant test run and code review.

### Required shared tests
- Forged tenant ID cannot change server-derived tenant context.
- Cross-tenant ticket, document, Redis, Kafka and vector access is denied.
- Duplicate event delivery creates no duplicate business operation.
- Refund execution is idempotent and requires approval.
- Poison messages reach DLQ and can be replayed by an operator.
- Ollama outage does not stop ticket creation.
- AI worker crash resumes from checkpoint.
- No secrets or raw PII appear in logs.

### Definition of Done
A feature is not complete merely because code was generated. It is complete only when requirements, implementation, tests, security review, observability, docs and phase evidence are all updated and verified.

## 8. Required living project files

Maintain these files throughout development:

| File | Required contents |
|---|---|
| `docs/agent/PROJECT_STATE.md` | Current phase, completed milestones, environment status, component readiness |
| `docs/agent/IMPLEMENTATION_PLAN.md` | Active tasks, owners, dependencies, acceptance criteria and status |
| `docs/agent/TASK_LOG.md` | Chronological actions, files changed, commands/tests and outcomes |
| `docs/agent/TEST_STATUS.md` | Test matrix, latest results, coverage and tenant isolation status |
| `docs/agent/BLOCKERS.md` | Bugs, environment issues, risks, assumptions and decisions needed |
| `docs/agent/AGENT_WORKFLOW.md` | Verified Antigravity capabilities, roles, workflows and orchestration rules |

Also create one reverse-engineering guide per major feature under `docs/reverse-engineering/`, organized by phase.

Each guide must explain: business problem, architectural choice and alternatives, internal steps, patterns, success/failure flow, component data flow, security/tenant boundaries, important classes/methods/configuration/SQL, and how to reproduce/debug it.

## 9. Hardware, resource and cost controls

The project target is 16 GB RAM on Windows. Treat blueprint estimates as planning estimates, not guaranteed usage. Measure with `docker stats`.

- Cap JVM heaps initially around `-Xmx256m` to `-Xmx384m` per service, then measure.
- Set Docker container memory limits and WSL2 memory limits in `.wslconfig`.
- Run `core` first; add `ai`, `obs` and `tools` only when needed.
- Do not run `full` Compose alongside kind.
- Start with one generation model and one embedding model.
- Keep a configurable mock LLM for tests and load runs.
- Prefer lightweight local models; benchmark RAM, latency and tool-call quality before locking versions.
- Do not load Ollama models concurrently without a measured reason.
- Use small local datasets except during intentional benchmark experiments.
- Do not use paid APIs, managed databases, cloud infrastructure or real payment/email integrations in the critical path.
- Real cloud deployment is out of scope for v1; Terraform is validate/plan/cost-estimate only.

**Important capacity distinction:** k6 tests against a mock LLM measure platform throughput under the mock's configured behaviour. They do not prove that a laptop can run 10k real LLM tickets/hour. Report platform load and real-model quality/inference measurements separately.

## 10. Approval boundaries

Antigravity may autonomously create/edit project files, implement features, run builds/tests, debug, apply non-destructive local configuration and run safe Docker commands.

Obtain explicit user approval before:
- Deleting persistent data or Docker volumes.
- Running `docker compose down -v` or destructive prune commands.
- Dropping/resetting databases.
- Destructive Git operations such as hard reset or force push.
- Installing global system packages.
- Pushing commits or publishing releases.
- Deploying externally or accessing external accounts/credentials.
- Any action that can incur cost.

Never disable security or permission safeguards to make a task pass.

## 11. First Antigravity session — exact execution order

The first session must perform planning and Phase 0 only. It must not start implementing the entire application.

1. Read `AGENTS.md`, `AGENT.md`, `guidlines.md` and `ENTERPRISE_AI_PLATFORM_BLUEPRINT.md` completely.
2. Inspect the existing repository and report its current state before modifying anything.
3. Inspect the installed Antigravity capabilities listed in Section 5.1 and record only verified capabilities.
4. Create/confirm the root instruction files and repository layout.
5. Create the `docs/agent/` state files.
6. Execute Phase 0 tasks in Section 6.
7. Run architecture and security reviews.
8. Update `PROJECT_STATE.md`, `TASK_LOG.md`, `TEST_STATUS.md`, `BLOCKERS.md` and this plan.
9. Produce a Phase 0 completion report listing files created, decisions made, unresolved questions and Phase 1 readiness.
10. Stop at the Phase 0 exit gate. Do not silently proceed if architecture, security or critical assumptions remain unresolved.

After Phase 0 is accepted, begin Phase 1 and continue one phase at a time.

## 12. Per-phase completion report format

At the end of every phase, Antigravity must report:

1. Phase name and status: `COMPLETE`, `BLOCKED` or `IN PROGRESS`.
2. Summary of delivered functionality.
3. Files created, modified and deleted (deletions require approval).
4. Architecture decisions and ADR references.
5. Commands executed.
6. Tests executed and exact pass/fail results.
7. Security and tenant-isolation results.
8. Observability evidence.
9. Documentation and reverse-engineering guides created/updated.
10. Known limitations and blockers.
11. Resource usage where relevant.
12. Next phase and its prerequisites.

Never mark a task complete without evidence.

---

## Appendix A — Main end-to-end acceptance scenario

For the empty-package refund ticket:

1. Gateway validates JWT and derives tenant context.
2. Ticket Service creates the ticket and outbox event in one transaction.
3. Outbox publisher sends `ticket.created.v1` to Kafka using `tenantId` as partition key.
4. AI consumer records the event in its inbox and starts triage.
5. RAG retrieves only the tenant's permitted refund policy.
6. Resolution agent drafts a cited response.
7. Action agent checks refund eligibility and shipping status through allow-listed tools.
8. Critic verifies grounding and policy compliance.
9. Workflow Service requests human approval for the refund.
10. Human approves/rejects.
11. Approved refund simulation executes idempotently.
12. Ticket state and customer notification are updated through owning services.
13. Audit, usage, citations, tool calls, tokens, latency and traces are recorded.
14. Duplicate delivery, worker crash, LLM outage and retry/DLQ behaviour are tested.

## Appendix B — First-week prerequisites from the blueprint

Verify the installed versions before changing the machine:

- Docker Desktop with WSL2.
- Java 21.
- Python 3.12.
- Node.js LTS.
- kind, kubectl, Helm, Terraform and k6 when their phases require them.
- WSL2 memory/swap configuration.
- Ollama with the proposed lightweight generation and embedding models only after the foundation is stable.

Do not install global packages or modify system-level configuration without user approval. Prefer project-local tooling and document missing prerequisites.

---

**End of implementation plan.** This plan is subordinate to the frozen architecture in `ENTERPRISE_AI_PLATFORM_BLUEPRINT.md`. Any deviation must be justified, reviewed and recorded in a new ADR.
