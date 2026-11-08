# Enterprise AI Platform: Final Project Blueprint

> **Multi-Tenant, Event-Driven AI Support, Knowledge and Workflow Automation Platform**
> Java (Spring Boot) control plane + Python (FastAPI/LangGraph) AI plane.
> Built **100% free and local-first**, designed to be **cloud-ready**. Production-grade premium alternatives are documented in Section 20 and the next-version plan in Section 21.

**Document status:** Frozen baseline v1.0. Changes to technology choices must go through an ADR (Section 17), not casual replacement.
**Audience:** The developer (you), and any coding agent that works on this repo. Section 24 contains the instructions to give a coding agent.

---

## Table of Contents

1. Motive and guiding principles
2. Problem statement
3. Scope: what we build, what we defer
4. Product definition and end-to-end scenario
5. Architecture
6. Technology stack (all free)
7. Service catalogue and ownership
8. Data architecture and multi-tenancy
9. Kafka and event architecture
10. Distributed-systems reliability
11. API engineering
12. AI plane (RAG, agents, memory, guardrails, evaluation, versioning)
13. Security architecture
14. Observability, logging and debugging
15. Testing strategy
16. DevOps: Docker, Kubernetes, Terraform, CI/CD
17. Engineering documentation and ADRs
18. Local hardware strategy (16 GB)
19. Scalability, capacity and cost modelling
20. Free vs premium: what real production would use
21. Next-version plan (v2, paid/cloud)
22. Repository structure
23. Roadmap and phases
24. Instructions for the coding agent
25. Definition of Done and engineering standards
26. Career evidence, interview prep and honest claims
27. Reminders and warnings
28. Appendices (event envelope, sample schemas, env vars, checklists)

---

## 1. Motive and guiding principles

**Motive:** Build one serious, end-to-end system that proves you can design, build, secure, observe, debug, scale and cost a distributed system with an AI plane, without spending money. The goal is engineering evidence for SDE-2 interviews (with SDE-3-level design depth), not a list of buzzwords.

**Core rules (non-negotiable):**

1. **Cost: ₹0.** Only free and open-source software, local execution. Nothing in the critical path requires a paid API or cloud account.
2. **Every technology must solve a real problem.** Do not add Kafka to say "Kafka"; build the failure scenario (duplicate event, crash, poison message) and prove the design handles it.
3. **Every architectural decision has a written reason** (ADR with alternatives and trade-offs).
4. **Every performance claim has a measurement.** No number goes on a resume or in docs unless a recorded test produced it.
5. **Core business must work without AI.** If Ollama is down, ticket creation continues and AI work queues up.
6. **Cloud-ready, not cloud-deployed.** Dockerfiles, Helm/Kustomize, Terraform and CI are written and validated, but never applied to a real cloud.
7. **Design first, then build bounded context by bounded context.** Do not generate the whole codebase in one shot.
8. **Honesty:** the project is solo-built; do not claim production experience or real-cloud results you do not have. Say "load-tested against a mock LLM" where that is true.

---

## 2. Problem statement

> Mid-size companies handle tens of thousands of support tickets a day across email, chat and API. Support agents waste time searching scattered documents, repeating answers, and escalating wrongly. Existing chatbots hallucinate, leak data across customers, and cannot take actions safely.
>
> Build a **multi-tenant, event-driven platform** that ingests tickets in real time, classifies and prioritises them, retrieves tenant-specific knowledge, drafts or resolves answers using collaborating AI agents, remembers customer history, enforces guardrails and PII protection, supports human approval for risky actions, and gives operators full observability, cost tracking and AI-quality evaluation, designed to scale to **10k+ tickets/hour** with strict tenant isolation.

---

## 3. Scope

### 3.1 In scope (build)

Identity and tenancy, ticketing with SLA, knowledge management, RAG, multi-agent AI with tools and approvals, AI memory, workflow automation, usage metering and governance, notifications and webhooks, developer API with API keys, observability, security hardening, testing, local Kubernetes, Terraform (validated only), load and chaos experiments, DR drills, documentation.

### 3.2 Deferred (build only when a concrete need or experiment justifies it)

| Item | Reason deferred |
|---|---|
| Complex React frontend | Backend and AI are the focus. Build a **minimal** admin UI only. |
| GraphQL | REST is enough. |
| Elasticsearch/OpenSearch | Postgres FTS + Qdrant cover needs. |
| Istio / service mesh | Overhead; do as a comparison experiment later. |
| Temporal | Evaluate **after** the Java workflow state machine works (Phase 12). |
| ClickHouse, Citus | Only for analytics/sharding experiments. |
| Multi-region, GPU cluster, real cloud deploy | Out of scope for ₹0. |
| LLM fine-tuning | RAG + evaluation first. |
| Real payments, real email | Simulated tools and local mail catcher. |

---

## 4. Product definition

An enterprise SaaS where **multiple organisations (tenants)** independently manage customers, tickets, knowledge, AI agents and automated workflows. Supports both human-operated and AI-operated flows.

**Example tenants:** Company A (e-commerce), Company B (bank support), Company C (software company), each with its own users, documents, tickets, AI configuration and quotas.

### 4.1 End-to-end scenario (the demo you will defend in interviews)

Customer ticket: *"My order was delivered but the package is empty. I want a refund."*

1. **Create (sync):** Gateway authenticates (Keycloak JWT), resolves tenant server-side, Ticket Service creates the ticket and writes an **outbox event in the same DB transaction**.
2. **Publish (async):** Outbox publisher sends `ticket.created.v1` to Kafka (partition key = tenantId).
3. **Triage:** AI worker (idempotent via inbox) classifies priority, sentiment, category, language.
4. **Retrieve:** RAG fetches tenant-specific refund policy chunks (hybrid search, rerank, permission filter).
5. **Reason:** Supervisor routes to Resolution agent, which drafts an answer with citations.
6. **Tool use:** Action agent calls `check_refund_eligibility` and `get_shipping_status` (allow-listed, tenant-scoped).
7. **Verify:** Critic agent checks groundedness and policy compliance.
8. **Approve:** Refund is high-risk, so Workflow Service creates an approval; a human approves/rejects.
9. **Execute:** Approved refund executes with an **idempotency key** (no double refund even if the event is delivered twice).
10. **Resolve and notify:** Ticket state updated, customer notified (simulated), audit trail written.
11. **Record:** Trace, tool calls, citations, tokens, latency, cost and outcome stored for evaluation and debugging.

### 4.2 Domains

| Domain | Responsibilities |
|---|---|
| Identity and tenancy | Organisations, users, roles, teams, quotas, configuration |
| Support management | Tickets, comments, assignment, SLA, escalation |
| Knowledge management | Documents, versions, ingestion, permissions |
| Workflow automation | Triggers, conditions, actions, approvals |
| AI platform | Models, RAG, agents, memory, guardrails, evaluation, versioning |
| Usage and governance | Token metering, quotas, audit, policies |
| Notifications | In-app, email simulation, signed webhooks |
| Platform operations | Dashboards, alerts, incidents, analytics |

---

## 5. Architecture

### 5.1 Two planes plus shared infrastructure

- **Java control plane (Spring Boot):** business operations, auth, tenancy, data consistency, APIs, workflow state, event publishing.
- **Python AI plane (FastAPI + LangGraph):** LLM calls, retrieval, embeddings, reranking, agents, guardrails, memory, evaluation.
- **Shared infra:** PostgreSQL, Redis, Kafka (KRaft), Qdrant, MinIO, Ollama, observability stack.

Communication rule: **REST/gRPC for immediate request-response; Kafka for decoupled business events.** gRPC is used only where justified (e.g., Java to Python sync calls), not everywhere.

### 5.2 Logical architecture (target)

```
                        +-----------------------------------+
 Clients / Webhooks --> |  Spring Cloud Gateway (BFF)        | <-- Keycloak (OIDC/JWT)
 React admin console    |  authn, tenant resolve, rate limit |
                        +----------------+------------------+
                                         |
   +-------------+-------------+---------+---------+---------------+-------------+
   v             v             v                   v               v             v
Identity/     Ticket        Knowledge          Workflow       Notification   Usage/Audit
Tenant        (state m/c,   (docs, versions,   (workflows,    (in-app,       (tokens,
(tenants,     SLA, outbox)  ingest status)     approvals,     webhooks)      quotas,
roles, quotas)                                  saga)                         audit)
   |             |             |                   |               |             |
   +------------ PostgreSQL (logical DB per service, RLS) ---------+-------------+
                 Redis (cache, rate limit, ephemeral)
                                         |
                           +-------------v--------------+
                           |      KAFKA (KRaft)          |
                           |  partition key = tenantId   |
                           |  retry topics + DLQ         |
                           +-------------+--------------+
                                         |
   +-------------------- PYTHON AI PLANE (FastAPI + workers) ---------------------+
   |  Event consumer (inbox) -> Supervisor (LangGraph)                            |
   |   Triage | Retrieval | Resolution | Action(tools) | Critic | Escalation        |
   |  Input guardrails -> Context assembler -> LLM -> Output guardrails            |
   |  RAG: ingest -> chunk -> embed -> hybrid (FTS+vector, RRF) -> rerank -> cite  |
   |  Memory: Redis (short) | Postgres+Qdrant (long, episodic) | forget-me         |
   |  Evaluation | Prompt/workflow versioning | Model adapter (+ mock LLM)         |
   +---------+------------------+------------------------+------------------------+
             v                  v                        v
          Ollama            Qdrant                    MinIO
      (LiteLLM optional)   (vectors)             (S3-compatible files)

 OBSERVABILITY: OTel SDKs -> OTel Collector -> Tempo (traces) | Prometheus (metrics) | Loki (logs)
                Phoenix (LLM traces/evals)  ->  Grafana (single pane)
```

### 5.3 Dependency rules (enforced in code review)

1. Services never write to another service's tables or share entities/repositories.
2. AI workers never update ticket tables directly; they emit events or call the owning service's API.
3. Knowledge Service owns document metadata; AI ingestion owns its processing pipeline.
4. Workflow Service owns approval state.
5. Usage Service consumes metering events; it does not query AI internals.
6. Notification Service owns delivery state.
7. Audit events are append-only.
8. All cross-service calls are authenticated and traceable (W3C `traceparent`).
9. Start with 6 logical Java services (not 15). Split only when a reason is documented.
10. The AI plane starts as **one Python application with separate modules/worker processes**; extract components later when scaling demands it.

---

## 6. Technology stack (all free)

### 6.1 Backend (Java)

| Tech | Purpose |
|---|---|
| Java 21 LTS, Spring Boot 3.x | Services |
| Spring Cloud Gateway | API gateway/BFF |
| Spring Security (OAuth2 resource server) | AuthN/AuthZ |
| Spring Data JPA/Hibernate, Flyway | Persistence and migrations |
| Spring Kafka | Kafka integration |
| Resilience4j | Circuit breaker, bulkhead, retry, rate limiter |
| Micrometer + OpenTelemetry Java agent | Metrics and tracing |
| Spring Cloud OpenFeign / RestClient, gRPC (selective) | Service calls |
| Quartz + ShedLock (JDBC) | Scheduled jobs |
| springdoc-openapi | API docs |
| MapStruct (optional) | DTO mapping |

### 6.2 AI plane (Python)

| Tech | Purpose |
|---|---|
| Python 3.12, FastAPI, Pydantic v2 | API, validation, structured output |
| LangGraph | Stateful agent orchestration (checkpointing, human-in-loop) |
| LangChain (selective only) | Integrations; avoid making it the framework for everything |
| Ollama | Local LLM + embeddings runtime |
| sentence-transformers | Reranker (cross-encoder) |
| Postgres FTS (tsvector) + Qdrant | Hybrid retrieval (keyword + vector), fused with RRF |
| Ragas + custom golden sets | Evaluation |
| Arize Phoenix | LLM tracing/evals (lighter than Langfuse, which needs ClickHouse) |
| LiteLLM proxy | **Optional** model gateway (routing, budgets, failover) |
| structlog, OpenTelemetry Python SDK | Logs, traces |
| aiokafka / confluent-kafka | Kafka client |
| pytest, httpx | Testing |

**Models (benchmark before locking):** generation `qwen2.5:3b-instruct` (alt: `llama3.2:3b`); embeddings `nomic-embed-text` (or `bge-m3` if RAM allows); reranker `bge-reranker-base` or a small MiniLM cross-encoder. Start with **one** generation model and **one** embedding model. Always keep a **mock LLM** server for load tests and as fallback.

### 6.3 Data and messaging

| Tech | Role |
|---|---|
| PostgreSQL 16 (RLS) | System of record, one container, logical DB per service |
| Redis | Cache, rate limits, idempotency keys, short-term memory |
| Apache Kafka (KRaft, no ZooKeeper) | Event bus |
| Apicurio Registry (or JSON Schema files initially) | Schema registry and evolution experiments |
| Qdrant | Vector DB (benchmark pgvector later as an ADR experiment) |
| MinIO | S3-compatible object storage |

### 6.4 Security

Keycloak (OIDC), Spring Security, PostgreSQL RLS, Vault dev mode (learning only; never call it production), Trivy, OWASP ZAP, Syft (SBOM), Cosign (signing experiment), OPA/Kyverno (policy-as-code experiment), gitleaks (secret scanning), OSV-Scanner/Dependabot.

### 6.5 DevOps and observability

Docker, Docker Compose (profiles), kind, Helm/Kustomize, KEDA (Kafka-lag autoscaling), HPA, Terraform (validate/plan only), tflint, tfsec/checkov, **Infracost** (cost estimates from Terraform), LocalStack (optional AWS emulation), GitHub Actions, Prometheus, Grafana, Loki + Alloy/Promtail, Tempo, OpenTelemetry Collector, Phoenix, k6, Toxiproxy, Testcontainers, WireMock, Mailpit (local email catcher), Unleash (feature flags, optional) or Spring Cloud Config.

### 6.6 Testing

JUnit 5, Mockito, Spring Boot Test, Testcontainers, REST Assured, Spring Cloud Contract (Pact only if a real consumer/provider need appears; do not run two contract systems), WireMock, pytest, httpx, k6, Toxiproxy, OWASP ZAP, Playwright (selective E2E).

---

## 7. Service catalogue and ownership

### 7.1 Java services (start with these six + gateway)

| Service | Responsibilities | Owned DB |
|---|---|---|
| Identity/Tenant | Tenant registration, config, users, memberships, roles, teams, quotas, API keys | `identity_db` |
| Ticket | Tickets, comments, attachments metadata, assignment, history, SLA, escalation, outbox | `ticket_db` |
| Knowledge | Documents, versions, approval, permissions, ingestion status | `knowledge_db` |
| Workflow | Workflow definitions, executions, approvals, saga state, retries | `workflow_db` |
| Notification | In-app notifications, webhook subscriptions, delivery attempts, DLQ | `notification_db` |
| Usage/Audit | Token and API metering, quotas, audit log, cost estimates, scheduled reports | `usage_db` |
| Gateway (infra, not domain) | AuthN, tenant resolution, rate limit, routing | none |

### 7.2 Python AI plane modules

`ai_api`, `event_consumer` (inbox), `supervisor`, `agents/` (triage, retrieval, resolution, action, critic, escalation, summarisation), `rag/` (ingest, chunk, embed, retrieve, rerank, cite), `memory/`, `guardrails/`, `context/` (assembler, token budget, compression), `model_adapter/` (Ollama, mock, optional LiteLLM), `evaluation/`, `workflow_versions/` (prompt/model/tool versions, canary, A/B), `tools/` (allow-listed tool registry), `observability/`. DB: `ai_db` (conversations, checkpoints, memory metadata, chunks/FTS, evaluation runs, workflow versions). **`ai_db` is never a second source of truth for tickets or tenants.**

### 7.3 Scheduled jobs

KB re-sync and re-index, memory consolidation/decay, daily evaluation runs, SLA breach checks and stale-ticket escalation, usage/cost reports, data-retention cleanup, outbox cleanup, DLQ alerts. Quartz + ShedLock so only one instance runs each job.

---

## 8. Data architecture and multi-tenancy

### 8.1 Database principles

Normalisation with selective denormalisation, PKs/FKs/unique constraints, composite, partial and GIN indexes, optimistic locking (`version` column), explicit transaction isolation choices, connection pooling (HikariCP; PgBouncer only when justified), Flyway migrations, retention/archival, backup and restore (including PITR experiment), partitioning by date/tenant where justified.

### 8.2 Multi-tenancy strategy

**Shared schema per service with `tenant_id` on every tenant-owned row**, enforced in layers:

1. Tenant identity derived from the **validated JWT**, never from client-supplied fields.
2. Server-side tenant resolution in the gateway; downstream services re-validate.
3. **PostgreSQL Row-Level Security.** Tenant context is set **per transaction** (`SET LOCAL app.tenant_id = ...`) and cannot leak across pooled connections. Add a negative test proving it.
4. Tenant-scoped repository queries (defence in depth).
5. Redis key namespacing `t:{tenantId}:...`.
6. Kafka: `tenantId` in the envelope and as the partition key; consumers validate tenant against the payload; ACL experiments.
7. Qdrant payload filter on `tenant_id` (mandatory in the retrieval layer, not optional per call).
8. MinIO key prefix `tenants/{tenantId}/...` plus access checks.
9. Automated **Tenant Isolation Test Suite** (Section 15).

Hot-tenant handling: monitor partition skew, per-tenant rate limits and quotas, document the sharding/partition-expansion strategy.

### 8.3 Core entities (conceptual)

```
Tenant
 +-- UserMembership, Team, Role, Permission, TenantConfiguration, ApiKey
 +-- Customer
 |    +-- Ticket
 |         +-- TicketComment, TicketAttachment, TicketAssignment, TicketHistory, TicketSLA
 +-- KnowledgeDocument
 |    +-- DocumentVersion, IngestionJob, DocumentPermission
 +-- WorkflowDefinition
 |    +-- WorkflowExecution
 |         +-- Approval
 +-- UsageRecord, AuditEvent
 (infra tables per service: outbox_event, inbox_event, idempotency_key, shedlock)
```

### 8.4 Ticket state machine (enforced, no arbitrary status updates)

```
NEW -> OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
                  |   ^
                  |   +-- WAITING_FOR_CUSTOMER
                  +------ WAITING_FOR_APPROVAL
RESOLVED -> OPEN (reopen)
```

### 8.5 Database performance laboratory

Generate datasets of 10k, 100k, 1M and 10M tickets. Compare latency and `EXPLAIN (ANALYZE, BUFFERS)` before and after: composite index, partial index, GIN (FTS), partitioning by date, cursor vs offset pagination, RLS overhead. Save plans and numbers in `docs/performance/database-benchmarks.md`.

---

## 9. Kafka and event architecture

### 9.1 Topic catalogue (all versioned, partition key `tenantId`)

| Topic | Producer | Consumers |
|---|---|---|
| `ticket.created.v1` | Ticket | AI, SLA, Audit |
| `ticket.updated.v1` | Ticket | Notification, Audit |
| `ticket.assigned.v1` | Ticket | Notification |
| `ticket.resolved.v1` | Ticket | Analytics, Notification |
| `knowledge.ingestion.requested.v1` | Knowledge | AI ingestion |
| `knowledge.ingestion.completed.v1` | AI | Knowledge |
| `knowledge.document.deleted.v1` | Knowledge | AI indexing |
| `ai.task.requested.v1` | Ticket/Workflow | AI workers |
| `ai.task.completed.v1` | AI | Workflow, Ticket |
| `ai.task.failed.v1` | AI | Workflow, Audit |
| `approval.requested.v1` | Workflow | Notification |
| `approval.completed.v1` | Workflow | AI, Workflow |
| `notification.requested.v1` | Business services | Notification |
| `usage.metered.v1` | AI, Gateway | Usage |
| `audit.event.v1` | All | Audit |
| `<topic>.retry.<n>` / `<topic>.dlq` | Framework | Retry workers / operators |

### 9.2 Standard event envelope

See Appendix A. Always include `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `aggregateId`, `correlationId`, `causationId`, `traceparent`, `producer`, `payload`. Start with JSON Schema; use Apicurio to practise compatibility rules (backward-compatible changes only within a version).

### 9.3 Partitioning

Key by `tenantId` (per-tenant ordering). Use a modest fixed partition count (e.g., 6), monitor skew, never assume one tenant owns one partition, and document the effect of partition expansion on ordering.

---

## 10. Distributed-systems reliability

Kafka is at-least-once; the system must give **effectively-once business results**.

| Pattern | Implementation goal |
|---|---|
| Transactional outbox | Business write and event record commit atomically; a publisher relays to Kafka |
| Inbox / dedup | Consumers record `eventId`; duplicates are skipped |
| Idempotency keys | `Idempotency-Key` header on APIs; idempotency key on every external side effect (refund) |
| Retry | Exponential backoff + jitter via retry topics; retry budgets |
| DLQ and replay | Poison messages isolated, inspectable, replayable by an operator tool |
| Saga (orchestrated) | Workflow Service coordinates multi-step flows with compensating actions |
| Optimistic locking | `version` column for concurrent updates |
| Pessimistic / distributed lock | Only where proven necessary (e.g., `SELECT ... FOR UPDATE SKIP LOCKED` for outbox, ShedLock for jobs) |
| Circuit breaker, bulkhead, timeout | Resilience4j on every outbound dependency; timeout propagation |
| Graceful degradation | Ollama down means ticket creation continues, AI tasks queue, UI shows "AI pending" |
| Load shedding and backpressure | Per-tenant rate limits, bounded worker concurrency, pause consumers on overload |
| Chaos tests | Toxiproxy/kill containers: broker down, DB slow, LLM timeout, duplicate and out-of-order events |

**Must-pass scenarios (write each as an automated test or scripted drill):**

1. Same `ticket.created` delivered twice produces one ticket and one AI task.
2. Refund executes, but the ticket-status update fails; recovery via saga compensation/retry, with **no double refund**.
3. AI worker crashes mid-workflow, then resumes from the LangGraph checkpoint.
4. Poison message goes to the DLQ after N retries, the consumer keeps going, and it is replayable.
5. Ollama down: tickets still created, AI backlog drains after recovery.
6. Out-of-order events for the same ticket handled via version/sequence checks.
7. Hot tenant floods the system without starving other tenants.

---

## 11. API engineering

- Versioned paths `/api/v1/...`; OpenAPI generated and published; contract tests.
- Cursor-based pagination for large lists (offset allowed for small admin lists); dynamic filtering and sorting with allow-listed fields.
- `Idempotency-Key` on all POSTs that create/charge/execute.
- Rate limits: per user, per tenant, per API key, per endpoint (Redis token bucket).
- AuthN: OIDC/JWT via Keycloak; API keys (hashed at rest, scoped) for the developer API; AuthZ: RBAC + resource-level checks (+ ABAC for selected policies).
- Webhooks: HMAC-signed, timestamped (replay protection), retries with backoff, delivery log, DLQ.
- **Sync vs async rule:** ticket create/read are sync; classification, AI resolution, approvals, notifications, ingestion are async via Kafka.
- Standard error model (RFC 7807 problem+json), correlation id in every response header.

---

## 12. AI plane

### 12.1 RAG pipeline

**Ingestion:** upload (MinIO) -> validate type/size/malware-safe handling -> extract text (PDF/text/URL) -> clean -> **chunk** (recursive; parent-child; overlap tuned; semantic as experiment) -> metadata (tenant, doc, version, permissions, source, page) -> embed -> upsert to Qdrant + chunk rows with `tsvector` in `ai_db`. Incremental re-index by content hash; delete propagates via `knowledge.document.deleted.v1`.

**Query:** input guardrails -> query analysis and rewriting (plus multi-query) -> **hybrid retrieval** (Postgres FTS + Qdrant vector, fused by Reciprocal Rank Fusion) -> **mandatory tenant + document-permission filter** -> rerank (cross-encoder) -> context compression -> generation -> **citation verification** (every claim maps to retrieved chunks) -> output guardrails.

**Retrieval benchmark:** compare chunk sizes, embeddings, hybrid vs vector-only, reranker on/off; report precision@k, recall@k, MRR on a golden set.

### 12.2 Agents (LangGraph)

| Agent | Responsibility |
|---|---|
| Supervisor | Routes between agents, enforces max iterations, timeouts, token budget, fallback to human |
| Triage | Intent, priority, sentiment, language, category |
| Retrieval | Calls RAG with tenant scope |
| Resolution | Drafts grounded answer with citations |
| Action/Tool | Executes allow-listed tools with schema-validated arguments |
| Critic/Verifier | Groundedness, policy and safety check before anything is sent |
| Escalation | Decides human handoff |
| Summarisation | Conversation and memory summaries |

Capabilities: structured outputs, conditional routing, **checkpointing and resume** (Postgres checkpointer), human-in-the-loop interrupt for approvals, cancellation, retries, per-run token and iteration budgets, full execution history.

**Tool catalogue (simulated backends):** `get_ticket_details`, `search_customer_orders`, `check_refund_eligibility`, `get_shipping_status`, `search_knowledge_base`, `update_ticket_status`, `create_internal_note`, `request_human_approval`, `send_customer_notification`. No arbitrary SQL, shell or network access for agents.

**Permission matrix:**

| Action | AI permission |
|---|---|
| Read ticket / search knowledge | Allowed (tenant + document auth) |
| Suggest refund, check eligibility | Allowed (tool permission) |
| Execute refund | **Human approval required** |
| Delete customer data | Explicit authorisation + approval |
| Change tenant settings | Forbidden |
| Arbitrary shell/SQL | Forbidden |

### 12.3 Context layer

Context assembly order: system policy -> tenant config -> customer profile -> retrieved docs (with citations) -> conversation summary -> current task. Token budgeting per section, compression/summarisation when over budget, versioned prompt templates stored in files + DB, hot-reloadable per tenant via config/flags.

### 12.4 Memory layer

| Type | Storage |
|---|---|
| Execution state | LangGraph checkpoints (Postgres) |
| Short-term conversation | Redis with TTL |
| Durable workflow state | Postgres (Workflow Service / `ai_db`) |
| Long-term semantic memory | Qdrant + Postgres metadata |
| Episodic ("what worked last time") | Postgres (+ vectors where useful) |

Features: creation, retrieval with relevance scoring, summarisation, expiry/decay, user and tenant boundaries, **forget-me / delete flow**, memory audit. Memory is purpose-driven and permission-aware; never store every message forever.

### 12.5 Guardrails

- **Input:** prompt-injection detection (heuristics + classifier prompt), PII redaction (regex + Presidio), toxicity, length limits.
- **Retrieval/document:** treat retrieved text as untrusted data (indirect prompt injection), strip instructions, delimit content.
- **Output:** groundedness check, citation verification, policy compliance, JSON schema validation, PII leak check.
- **Action:** tool allow-list, argument validation, approval gates, rate limits.
- **Red-team suite:** direct and indirect injection, cross-tenant retrieval attempts, malicious tool arguments, unauthorised tool calls, data exfiltration attempts, fabricated citations. Runs in CI.
- Options: custom rule engine first; NeMo Guardrails / Guardrails AI as comparisons.

### 12.6 Model engineering

Model adapter interface (`generate`, `generate_structured`, `embed`, `health_check`) with Ollama adapter, mock adapter, optional LiteLLM and optional free-tier API adapters (Groq/Gemini; **never required**, quotas can change). Routing by task (small model for triage, larger for resolution), fallback on failure/timeout, semantic cache in Redis for repeated questions, batching, token optimisation, model benchmarking (quality vs latency vs memory).

### 12.7 Evaluation and AI workflow versioning

- Golden datasets (tickets + expected category, expected citations, expected tool calls).
- Metrics: faithfulness/groundedness, answer relevance, retrieval precision/recall, tool-call accuracy, task completion, hallucination rate, latency, tokens/cost.
- **CI regression gate:** a prompt/model/workflow change cannot merge if quality drops below thresholds.
- **AI workflow versioning:** store workflow id, version, prompt version, model config, tool definitions, guardrail config, eval results, deployment state, rollback info.
- **Canary and A/B:** e.g., 90% v1 / 10% v2, compare quality, completion, latency, cost; promote only if thresholds pass; one-click rollback.

---

## 13. Security architecture

Layers: TLS/gateway -> Keycloak authN -> JWT validation -> server-side tenant resolution -> RBAC/ABAC -> service validation -> DB RLS -> audit logging.

| Layer | Controls |
|---|---|
| Application | OIDC/OAuth2, token expiry and revocation, CSRF/CORS, SQLi/XSS protection, SSRF protection on URL ingestion (block internal ranges), secure uploads (type, size, content sniffing, isolated storage), secrets never in Git |
| Multi-tenant | RLS, tenant filters everywhere, Redis/Kafka/Qdrant/MinIO isolation, cross-tenant tests |
| AI | Prompt injection defences, tool boundaries, output validation, PII redaction, approvals, sandboxed tools |
| Infrastructure | Trivy image scan, non-root containers, read-only FS where possible, NetworkPolicies, least-privilege roles, SBOM, gitleaks, dependency scanning, tfsec/checkov |

**Threat model:** STRIDE + trust boundaries + OWASP Top 10 + OWASP LLM Top 10, with mitigations and residual risks in `docs/security/threat-model.md`.

**Security test catalogue (each must exist as a test):** forged tenant id, cross-tenant ticket/doc/Redis/Kafka/vector access, expired/forged JWT, 401/403 paths, SQL injection, malicious upload, SSRF, webhook signature tampering, replayed webhook, duplicate refund, secret leakage in logs, prompt injection (direct/indirect), unauthorised tool call.

**Vault note:** dev mode is for learning only. Local secrets via `.env` (git-ignored) and Docker secrets; document the production path (Section 20).

---

## 14. Observability, logging and debugging

Set this up **first** (Phase 1), not last.

- **One trace per ticket:** OpenTelemetry context propagation over HTTP **and Kafka headers** (`traceparent`), Java agent + Python SDK, so one trace spans gateway -> ticket -> Kafka -> triage -> retrieval -> LLM -> critic -> approval.
- **Structured JSON logs** in both stacks (Logback JSON, `structlog`) with `trace_id`, `span_id`, `tenant_id`, `ticket_id`, `correlation_id`, `agent`, `workflow_version`. PII redacted before logging. Loki logs correlate to Tempo traces in Grafana.
- **Metrics (Prometheus):** request rate/latency/errors, Kafka consumer lag, DLQ depth, retry counts, outbox backlog, DB pool use, cache hit ratio, LLM latency, tokens per tenant, agent success/failure rate, retrieval quality, guardrail blocks.
- **AI tracing (Phoenix):** prompts, retrieved chunks, tool calls, scores, token counts per run.
- **Dashboards (Grafana):** Service health, Kafka and consumers, AI operations (cost, tokens, hallucination rate, task completion), tenant usage, SLO burn.
- **Alerts:** consumer lag, DLQ growth, p95 latency, error rate, outbox age, token cost per tenant, SLO burn rate.
- **Debug playbook** (`docs/operations/runbooks/debug-wrong-answer.md`): ticket X got a wrong answer -> find trace -> inspect retrieved chunks -> check critic score -> replay exact prompt/workflow version through the eval harness.
- **SLO/SLI:** e.g., 99.5% of valid ticket-creation requests succeed (rolling 30 days, test environment). AI availability is measured **separately**; AI failure must not break ticketing. Error budgets and burn-rate alerts documented.
- **Incident simulation:** at least two drills (e.g., consumer lag spike with rising AI latency; poison-message storm) with timeline and postmortem.

---

## 15. Testing strategy

| Layer | Tools / approach |
|---|---|
| Unit | JUnit 5, Mockito; pytest |
| Integration | Spring Boot Test + Testcontainers (Postgres, Kafka, Redis); pytest with real Qdrant/Redis containers |
| Contract | Spring Cloud Contract for REST; JSON Schema/Avro compatibility checks for events |
| E2E | Scripted scenario of Section 4.1; Playwright for the minimal UI |
| Failure injection | Toxiproxy (latency, drops), kill/restart containers, WireMock fault responses |
| Performance | k6 against a **mock LLM** (platform throughput) |
| Security | Tenant Isolation Suite, OWASP ZAP baseline, Trivy, gitleaks, red-team prompts |
| AI evaluation | Ragas + custom golden sets, regression gate in CI |

**Tenant Isolation Test Suite** attempts to access another tenant's tickets, documents, Redis keys, Kafka events and vector records, including forged `tenant_id` in headers, body and query; all must fail.

---

## 16. DevOps

### 16.1 Docker Compose profiles

| Profile | Contents | Purpose |
|---|---|---|
| `core` | Postgres, Redis, Kafka (KRaft), Keycloak, MinIO, Spring services, gateway | Backend and distributed-systems work |
| `ai` | FastAPI, workers, Ollama, Qdrant | RAG/agent work |
| `obs` | OTel Collector, Prometheus, Grafana, Loki, Tempo, Phoenix | Debugging, dashboards |
| `tools` | Mailpit, Kafka UI, Toxiproxy, Unleash (optional) | Dev helpers |
| `full` | All of the above | Occasional integration runs (needs measuring) |

Run `core` first; add `ai` and `obs` as needed. Do not run `full` and kind at the same time.

### 16.2 Containers

Multi-stage builds, non-root user, health checks, slim/distroless bases, pinned versions, `.dockerignore`, Trivy scan in CI, SBOM via Syft.

### 16.3 Kubernetes (kind, local only)

Helm charts or Kustomize overlays (`local`/`staging`/`prod`), resource requests/limits, liveness/readiness/startup probes, ConfigMaps/Secrets, HPA (CPU), **KEDA scaling AI workers on Kafka consumer lag**, PodDisruptionBudgets, NetworkPolicies (default deny, explicit allow), demonstrate rolling update, restart recovery, scaling under load.

### 16.4 Terraform (validate and plan only, never apply)

AWS reference modules: VPC, EKS, RDS Postgres (Multi-AZ), ElastiCache, MSK, S3, ECR, IAM (IRSA), Secrets Manager, optional GPU node group for vLLM, CloudWatch/managed Grafana optional. CI runs `terraform fmt`, `validate`, `tflint`, `tfsec`/`checkov`, and `infracost`. Remote-state config documented. **Docs must state clearly: "Terraform was validated and cost-estimated, not applied to a real cloud."**

### 16.5 CI/CD (GitHub Actions, free tier)

Pipelines: build and unit test (Java, Python) -> integration tests (Testcontainers) -> lint/static analysis -> Trivy + gitleaks + dependency scan -> AI eval gate -> contract checks -> `helm lint` + `terraform validate` -> build images (push to GHCR optional). Deploy job exists but is **disabled**.

---

## 17. Engineering documentation we must maintain

**This is non-negotiable.** A strong engineer should be able to explain the system, not merely show the source code. In interviews you will be asked "why", "what if it fails" and "how does it scale". These documents are your answers, written while the decisions are fresh.

### 17.1 Required documentation structure

```
docs/
│
├── product/
│   ├── product-requirements.md
│   ├── functional-requirements.md
│   ├── non-functional-requirements.md
│   └── use-cases.md
│
├── architecture/
│   ├── system-context.md
│   ├── hld.md
│   ├── lld.md
│   ├── service-boundaries.md
│   ├── data-flow.md
│   └── deployment-architecture.md
│
├── adr/
│   ├── 001-java-python-separation.md
│   ├── 002-kafka-vs-rabbitmq.md
│   ├── 003-qdrant-vs-pgvector.md
│   ├── 004-outbox-pattern.md
│   ├── 005-multi-tenancy.md
│   ├── 006-rest-vs-grpc.md
│   ├── 007-langgraph-orchestration.md
│   └── 008-temporal-evaluation.md
│
├── database/
│   ├── er-diagrams.md
│   ├── indexing-strategy.md
│   ├── tenancy-isolation.md
│   └── backup-recovery.md
│
├── messaging/
│   ├── kafka-topics.md
│   ├── event-contracts.md
│   ├── retry-dlq.md
│   └── event-versioning.md
│
├── ai/
│   ├── rag-design.md
│   ├── agent-architecture.md
│   ├── memory-design.md
│   ├── model-routing.md
│   ├── guardrails.md
│   └── evaluation-strategy.md
│
├── security/
│   ├── threat-model.md
│   ├── tenant-isolation.md
│   ├── ai-security.md
│   └── security-test-report.md
│
├── performance/
│   ├── load-test-report.md
│   ├── database-benchmarks.md
│   ├── kafka-benchmarks.md
│   └── capacity-model.md
│
├── operations/
│   ├── runbooks/
│   ├── incidents/
│   ├── postmortems/
│   ├── slo-sli.md
│   └── disaster-recovery.md
│
└── cost/
    └── infrastructure-and-inference-cost.md
```

**Additional docs added by this blueprint (beyond the required list):**

```
docs/
├── adr/009-postgres-fts-qdrant-hybrid-retrieval.md
├── adr/010-phoenix-vs-langfuse.md
├── adr/011-idempotency-strategy.md
├── cost/premium-vs-free.md                 # Section 20 with real quotes at time of writing
├── operations/runbooks/debug-wrong-answer.md
├── interview/question-bank.md
└── interview/design-defence-notes.md
```

### 17.2 What each document must contain

| File | Must answer |
|---|---|
| `product/product-requirements.md` | Who the users are, problems solved, goals, non-goals, success metrics |
| `product/functional-requirements.md` | Numbered, testable requirements per module (tickets, knowledge, workflow, AI, admin) |
| `product/non-functional-requirements.md` | Latency, availability, isolation, security, scalability, cost and observability targets, each with a measurement method |
| `product/use-cases.md` | Actors, main flows, alternate/failure flows (include the refund scenario from Section 4.1) |
| `architecture/system-context.md` | System boundary, actors, external systems |
| `architecture/hld.md` | Containers, responsibilities, communication styles, key flows, diagrams |
| `architecture/lld.md` | Per-service class/package design, state machines, sequence diagrams, outbox/inbox internals |
| `architecture/service-boundaries.md` | Bounded contexts, ownership, dependency rules (Section 5.3), what must never be shared |
| `architecture/data-flow.md` | Ticket lifecycle, ingestion flow, approval flow, with sync vs async marked |
| `architecture/deployment-architecture.md` | Compose profiles, kind/Helm layout, Terraform reference architecture (marked "validated, not applied") |
| `database/er-diagrams.md` | Entities and relations per logical DB, table ownership matrix |
| `database/indexing-strategy.md` | Each index, the query it serves, before/after `EXPLAIN ANALYZE` evidence |
| `database/tenancy-isolation.md` | RLS policies, transaction-scoped tenant context, pool-leak tests, shared-schema vs schema-per-tenant analysis |
| `database/backup-recovery.md` | Backup method, restore steps, PITR experiment, measured RPO/RTO |
| `messaging/kafka-topics.md` | Topic catalogue, keys, partitions, retention, producers, consumers |
| `messaging/event-contracts.md` | Envelope and per-event schemas, ownership, examples |
| `messaging/retry-dlq.md` | Retry topics, backoff and jitter, retry budgets, DLQ inspection and replay procedure |
| `messaging/event-versioning.md` | Compatibility rules, how to evolve a schema, deprecation process |
| `ai/rag-design.md` | Ingestion, chunking decisions, hybrid retrieval, reranking, citation verification, benchmark results |
| `ai/agent-architecture.md` | Agent roles, graph, tools, permissions, checkpointing, budgets, failure handling |
| `ai/memory-design.md` | Memory types, storage, expiry, forget-me flow, isolation |
| `ai/model-routing.md` | Models, routing rules, fallbacks, semantic cache, benchmark of quality vs latency vs memory |
| `ai/guardrails.md` | Input, retrieval, output and action guardrails, red-team results |
| `ai/evaluation-strategy.md` | Golden sets, metrics, thresholds, CI gate, canary/A-B promotion criteria |
| `security/threat-model.md` | STRIDE, trust boundaries, OWASP Top 10 and LLM Top 10 mapping, residual risks |
| `security/tenant-isolation.md` | Isolation per layer (DB, Redis, Kafka, Qdrant, MinIO) and the tests that prove it |
| `security/ai-security.md` | Prompt injection (direct and indirect), tool boundaries, exfiltration, approvals |
| `security/security-test-report.md` | Actual results of the security test catalogue, with dates and versions |
| `performance/load-test-report.md` | Method, mock-LLM parameters, results (p50/p95/p99, lag, errors), limits of the test |
| `performance/database-benchmarks.md` | 10k/100k/1M/10M datasets, plans before/after tuning |
| `performance/kafka-benchmarks.md` | Throughput, lag recovery, partition and consumer scaling |
| `performance/capacity-model.md` | 1k/10k/100k tickets/day, first bottleneck per scale, GPU/worker maths |
| `operations/runbooks/` | One file per alert/failure: symptom, diagnosis, mitigation, verification |
| `operations/incidents/` | Incident timelines from drills |
| `operations/postmortems/` | Blameless postmortems: impact, root cause, fix, follow-ups |
| `operations/slo-sli.md` | SLIs, SLOs, error budgets, burn-rate alerts (AI measured separately from ticketing) |
| `operations/disaster-recovery.md` | Failure scenarios, backup scope, RPO/RTO targets and measured values |
| `cost/infrastructure-and-inference-cost.md` | Infra cost at three scales (from Infracost), cost per ticket, GPU vs API break-even, savings levers measured |

### 17.3 Mandatory ADR format

Every significant architectural decision is one file in `docs/adr/`, numbered, in this exact format:

1. **Context**: the situation and constraints.
2. **Problem**: the specific question to decide.
3. **Options considered**: at least two real alternatives.
4. **Decision**: what we chose.
5. **Reasons**: why, tied to our requirements.
6. **Advantages**
7. **Disadvantages**
8. **Consequences**: what this commits us to, what becomes easier or harder.
9. **Revisit conditions**: the concrete signals that would make us reconsider.

**Do not merely write** "We selected Kafka because it is scalable."
**Instead explain** why Kafka fits our event-retention, replay, partitioning, consumer-group and throughput requirements better than the alternatives considered.

#### ADR template

```markdown
# ADR-00X: <Title>

Status: Proposed | Accepted | Superseded by ADR-0YY
Date: YYYY-MM-DD

## 1. Context
## 2. Problem
## 3. Options considered
### Option A: ...
### Option B: ...
## 4. Decision
## 5. Reasons
## 6. Advantages
## 7. Disadvantages
## 8. Consequences
## 9. Revisit conditions
```

#### Worked example (shape to follow for ADR-002, Kafka vs RabbitMQ)

- **Context:** Many tickets per tenant per hour; AI tasks are slow and bursty; several consumers (AI, SLA, audit, notification) need the same event independently.
- **Problem:** Choose the broker for inter-service events.
- **Options:** Kafka (KRaft); RabbitMQ; Postgres-as-queue.
- **Decision:** Kafka.
- **Reasons:** (a) retained log lets us **replay** events after a consumer bug or when adding a new consumer; (b) partition key `tenantId` gives per-tenant ordering while scaling by partitions; (c) independent **consumer groups** let AI, audit and notification read the same event; (d) lag is a first-class metric that KEDA can scale on; (e) high sequential throughput suits the load-test target.
- **Advantages:** replay, ordering per key, consumer scaling, ecosystem (schema registry, Kafka UI).
- **Disadvantages:** heavier memory footprint on a 16 GB laptop; operational complexity; no per-message priority or delay; at-least-once only, so we must build outbox/inbox/idempotency.
- **Consequences:** every consumer must be idempotent; we must manage partition counts and hot tenants; single-node KRaft only locally.
- **Revisit when:** local memory budget cannot be met; throughput stays tiny (RabbitMQ would suffice); or per-message routing/priority becomes central.

### 17.4 When each document is produced

| Phase | Documents created or updated |
|---|---|
| 0 | All `product/*`, `architecture/system-context|hld|service-boundaries|data-flow`, ADR 001-008 (draft), `kafka-topics`, `event-contracts` (draft), `threat-model` (draft), `er-diagrams` (draft) |
| 1 | `deployment-architecture` (local), `slo-sli` (draft) |
| 2 | `database/tenancy-isolation`, `security/tenant-isolation`, ADR-005 finalised |
| 3 | `lld` (ticket service), `indexing-strategy` (first pass) |
| 4 | `retry-dlq`, `event-versioning`, ADR-004 and ADR-011, first runbooks, chaos drill incident notes |
| 5 | `ai/rag-design`, retrieval benchmark, ADR-003 and ADR-009 |
| 6 | `ai/agent-architecture`, `ai/memory-design`, ADR-007 |
| 7 | `ai/model-routing`, `ai/guardrails`, `ai/evaluation-strategy`, ADR-010 |
| 9 | `security/ai-security`, `security-test-report`, final `threat-model` |
| 10 | `operations/*` (runbooks, incidents, postmortems, final `slo-sli`) |
| 11 | Final `deployment-architecture`, Terraform and Infracost notes |
| 12 | `performance/*`, `database-benchmarks`, `kafka-benchmarks`, `capacity-model`, `disaster-recovery`, `cost/*`, ADR-006 and ADR-008 |
| 13 | Final review of every file, `interview/*`, `cost/premium-vs-free.md` |

### 17.5 Documentation rules

1. A feature is not Done until its docs are updated (see Definition of Done, Section 25).
2. Docs that contain numbers must link to the script and raw output that produced them (`testing/...`).
3. Diagrams are kept as text (Mermaid) in the repo so they diff and review well.
4. ADRs are never deleted; a changed decision gets a new ADR that supersedes the old one.
5. Each doc starts with its owner service/area, last-reviewed date and status (draft / reviewed).
6. Runbooks must be tested at least once in a drill; mark them "verified on <date>".
7. If the architecture and the docs disagree, fix the docs in the same pull request.

---

---

## 18. Local hardware strategy (16 GB RAM)

Planning estimates only; **measure real usage** with `docker stats`.

| Group | Rough budget |
|---|---|
| OS + WSL2 + apps | 3-4 GB |
| Postgres + Redis + Kafka | 1.5-2.5 GB |
| Spring services | 1.5-3 GB (cap heap, e.g. `-Xmx256m` to `-Xmx384m` each) |
| Python AI | 0.5-1 GB |
| Ollama + 3B model | 2-4 GB |
| Qdrant + MinIO | 0.5-1 GB |
| Observability | 1-2 GB |

Rules: set Docker memory limits and JVM heap caps; limit WSL2 memory in `.wslconfig`; run one profile combination at a time; one generation model plus one embedding model; Phoenix not ClickHouse-based tools; avoid kind plus full Compose together; watch swap (swapping makes inference extremely slow); use small datasets for local experiments and scale up only for specific benchmarks. If RAM is only 8 GB, use a 1B-3B model, skip the `obs` profile except Prometheus/Grafana, and run services selectively.

---

## 19. Scalability, capacity and cost modelling

**Be honest about scale:** a local 3B-7B model yields roughly 5-20 tokens/s, so real LLM work cannot reach 10k tickets/hour on a laptop. Prove the platform and the model **separately**:

1. **Platform load test (k6) against a mock LLM** with realistic latency/tokens: measure tickets/s, p50/p95/p99, Kafka lag recovery, DB connections, cache hit ratio, retries, DLQ growth, back-pressure.
2. **Real-model quality test** at low volume: measure groundedness, relevance, hallucination rate, latency.
3. **Capacity model** (spreadsheet/doc): `GPUs needed = (tokens per ticket x tickets per hour) / (tokens per second per GPU x 3600)` with utilisation headroom.

**Scale scenarios:** 1k, 10k, 100k tickets/day. For each: expected first bottleneck (LLM, then vector search, then Kafka partitions, then Postgres writes), what to change (scale workers, add partitions, read replicas, partition tables, shard by tenant), and cost.

**Cost levers implemented and measured:** semantic cache hit rate, model routing, context compression, batching, per-tenant budgets, Kafka retention tiers.

**Lab targets (not claims until measured):** ticket create p95 < 300 ms and ticket read p95 < 200 ms under defined mock load; zero cross-tenant leaks in the isolation suite; zero duplicate business operations in duplicate-delivery tests; end-to-end trace for 100% of sampled tickets.

**DR:** backup/restore drills for Postgres, Qdrant snapshots, MinIO; define RPO/RTO targets and record measured restore times.

---

## 20. Free vs premium: what real production would use

Everything in the build uses the free column. Know the premium column so you can discuss it in interviews and use it in a future funded version.

| Concern | Free (this project) | Premium / managed (real production) | Approx. note |
|---|---|---|---|
| LLM | Ollama 3B-7B local | OpenAI, Anthropic, Gemini, Bedrock; or self-hosted vLLM on GPU (A10/L4/A100) | Per-token API pricing or GPU hourly; the biggest cost line |
| LLM gateway | LiteLLM OSS (optional) | LiteLLM Enterprise, Portkey, Helicone | Governance, SSO, budgets |
| Embeddings/reranker | Local sentence-transformers/Ollama | OpenAI/Cohere/Voyage embeddings, Cohere Rerank | Per-token |
| Vector DB | Qdrant self-hosted | Qdrant Cloud, Pinecone, Weaviate Cloud | Per node/storage |
| Kafka | Single-node KRaft | AWS MSK, Confluent Cloud | Per broker-hour / throughput |
| Postgres | Docker Postgres | AWS RDS/Aurora Multi-AZ, Cloud SQL | Instance + storage + backups |
| Redis | Docker Redis | ElastiCache, Redis Cloud | Per node |
| Object storage | MinIO | S3 | Per GB + requests |
| Identity | Keycloak self-hosted | Auth0, Okta, Cognito, Entra ID | Per MAU |
| Secrets | `.env`, Vault dev | Vault HA/HCP Vault, AWS Secrets Manager | Per secret/cluster |
| Observability | Prometheus/Grafana/Loki/Tempo self-hosted | Grafana Cloud, Datadog, New Relic, AMP | Per host/GB ingested |
| LLM tracing | Phoenix self-hosted | Langfuse Cloud, LangSmith, Arize AX | Per trace volume |
| Feature flags | Unleash OSS / config | LaunchDarkly | Per seat/MAU |
| Kubernetes | kind | EKS/GKE/AKS | Control plane + nodes |
| Autoscaling | KEDA + HPA | Same, plus cluster autoscaler/Karpenter | Node cost |
| CI/CD | GitHub Actions free | Larger runners, Argo CD/GitOps, Harness | Minutes-based |
| Security scanning | Trivy, ZAP, gitleaks | Snyk, Prisma Cloud, Wiz, GitHub Advanced Security | Per developer/asset |
| Email | Mailpit | SES, SendGrid, Postmark | Per email |
| Incident mgmt | Docs + drills | PagerDuty, Opsgenie, incident.io | Per user |
| Durable workflows | Java state machine | Temporal Cloud | Per action |
| Analytics | Postgres/Prometheus | ClickHouse Cloud, Snowflake, BigQuery | Per usage |
| DR | Local backup/restore drills | Cross-region replicas, AWS Backup | Storage + transfer |
| Cost estimate tool | Infracost free | Infracost Cloud | Optional |

**Rule:** keep every external integration behind an interface (model adapter, object-store client, notification sender, secret provider) so moving from the free to the premium column is a configuration change plus a new adapter, not a rewrite. Maintain `docs/cost/premium-vs-free.md` with real price quotes at the time you write it (prices change; do not hard-code numbers from memory).

---

## 21. Next-version plan (v2: paid/cloud), for later

Do **not** start this until v1 is complete and documented.

**v2.0 goals**

1. Deploy to a real cloud (AWS reference already in Terraform): EKS, RDS Multi-AZ, MSK, ElastiCache, S3, ECR, Secrets Manager, ALB, WAF.
2. Replace Ollama with a managed API or vLLM on a GPU node group; keep Ollama as dev/fallback via the model adapter.
3. Managed observability (Grafana Cloud or Datadog) or keep self-hosted on cluster with S3-backed Loki/Tempo.
4. GitOps with Argo CD, image signing (Cosign) and admission policies (Kyverno/OPA) enforced.
5. Real SSO (Auth0/Okta/Cognito), real email (SES), and a real payment sandbox (Stripe test mode) for refund tool experiments.
6. Run the same k6 suite on real infrastructure and replace "mock LLM" numbers with real ones.
7. Multi-AZ failover test, real PITR restore, cross-region backup copy.

**v2.1 and later (optional)**: Temporal for durable workflows (after the ADR-008 comparison), service mesh (mTLS), Citus or tenant-sharded Postgres, ClickHouse analytics, fine-tuning/LoRA experiments, SOC2-style control mapping, multi-region active-passive.

**Cost-control reminders for v2:** budgets and alerts first, tear down after demos (`terraform destroy`), use spot/small instances, free-tier credits, and always run `infracost` before apply.

---

## 22. Repository structure

```
enterprise-ai-platform/
+-- README.md
+-- AGENTS.md                      # rules for coding agents (Section 24)
+-- docker-compose.yml             # profiles: core, ai, obs, tools, full
+-- .env.example
+-- Makefile                       # make up-core, up-ai, up-obs, test, lint, load-test
+-- apps/
|   +-- admin-console/             # minimal React + TypeScript
+-- backend/                       # Java 21, Spring Boot
|   +-- common/                    # shared libs: event envelope, tenant context, outbox/inbox, error model
|   +-- gateway/
|   +-- identity-tenant-service/
|   +-- ticket-service/
|   +-- knowledge-service/
|   +-- workflow-service/
|   +-- notification-service/
|   +-- usage-audit-service/
+-- ai-plane/                      # Python 3.12
|   +-- app/
|   |   +-- api/  consumers/  supervisor/  agents/  tools/
|   |   +-- rag/  memory/  guardrails/  context/
|   |   +-- model_adapter/  evaluation/  workflow_versions/  observability/
|   +-- mock-llm/
|   +-- tests/
|   +-- pyproject.toml
+-- contracts/
|   +-- events/                    # JSON Schemas per topic version
|   +-- openapi/
+-- infra/
|   +-- docker/                    # compose fragments, Dockerfiles, configs
|   +-- observability/             # otel, prometheus, grafana dashboards, loki, tempo
|   +-- k8s/                       # helm charts / kustomize, keda, networkpolicies
|   +-- terraform/                 # modules + envs (validate only)
|   +-- keycloak/                  # realm export
+-- testing/
|   +-- load/ (k6)   chaos/ (toxiproxy)   security/ (isolation suite, zap)   ai-eval/ (golden sets)
+-- scripts/                       # seed data, dataset generators, replay-dlq tool
+-- docs/                          # structure in Section 17
+-- .github/workflows/
```

Create directories as each phase needs them; this is the target, not a requirement to scaffold everything on day one.

---

## 23. Roadmap and phases

No fixed deadline: milestone-based, quality over speed. Observability, testing and security are continuous, not end phases.

| Phase | Focus | Exit criteria |
|---|---|---|
| 0 | Requirements and architecture (PRD, HLD, bounded contexts, ADRs, threat model draft, schemas, event catalogue, API boundaries) | You can explain what it does, why each service exists, data flow, and failure scenarios |
| 1 | Foundation: monorepo, conventions, Compose (`core`), Postgres, Redis, Kafka, Flyway, CI, **OTel + logging skeleton** | One command starts core; health checks pass; CI builds and tests |
| 2 | Identity and tenancy: Keycloak, JWT, tenants, RBAC, RLS, Redis isolation | Two tenants cannot see each other's data (tests prove it) |
| 3 | Core ticketing: CRUD, state machine, assignment, comments, history, SLA, pagination, OpenAPI | Complete support workflow works **without AI** |
| 4 | Distributed systems: event contracts, outbox, inbox, idempotency, retry, DLQ, replay, saga, circuit breakers, chaos drills | Duplicates, crashes, downtime, poison messages handled without corrupting state |
| 5 | Knowledge and RAG: upload, MinIO, extraction, chunking, embeddings, Qdrant, hybrid search, rerank, citations, permissions | Grounded cited answers within permissions; retrieval benchmark done |
| 6 | Agentic AI: LangGraph supervisor, triage/retrieval/resolution/critic, tools, approvals, memory, checkpoint recovery | Agent processes a ticket, uses authorised tools, requests approval, recovers from crash |
| 7 | AI platform: model adapter, prompt/workflow versioning, routing, eval harness, CI gate, canary/A-B, metering, guardrails | Two workflow versions compared quantitatively; promote/rollback works |
| 8 | Product completion: workflow builder, SLA escalation, developer API/API keys, webhooks, tenant admin, usage dashboard, notifications, minimal UI | Major end-to-end user and admin flows work |
| 9 | Security hardening: threat model final, ZAP, isolation suite, prompt-injection suite, SSRF/upload tests, scanning | Critical tests pass; residual risks documented |
| 10 | Observability and operations: dashboards, alerts, SLOs, runbooks, incident drills, postmortems | Failed ticket diagnosed via one trace across everything |
| 11 | Kubernetes and DevOps: Helm, KEDA, HPA, NetworkPolicies, PDBs, Terraform validate, Infracost | Local kind deployment scales and recovers |
| 12 | Advanced experiments: DB partitioning, read replica, sharding design, hot tenants, Kafka rebalancing, DR/RPO/RTO, Temporal comparison, capacity and cost models | Decisions defended with data |
| 13 | Portfolio: final docs, diagrams, load/eval/security reports, demo video, interview bank | A stranger can run it, reproduce experiments, and challenge decisions |

**Order logic:** architecture -> environment -> identity -> ticketing (no AI) -> events -> knowledge/RAG -> agents -> evaluation -> security -> infra -> scale -> portfolio.

---

## 24. Instructions for the coding agent

Paste the following into `AGENTS.md` (or your agent's system prompt) and refer to this blueprint as the source of truth.

```
You are building the "Enterprise AI Platform" described in ENTERPRISE_AI_PLATFORM_BLUEPRINT.md.

RULES
1. Follow the blueprint. Do not change technologies without writing an ADR in docs/adr/.
2. Work one phase and one bounded context at a time. Never generate the whole system at once.
3. Before coding a feature: state the plan, list files to create/change, list tests to add.
4. Everything must be free/open-source and run locally. Never add a paid SDK or require cloud credentials. Keep external integrations behind interfaces (model adapter, storage client, notifier, secret provider).
5. Tenant isolation is mandatory: derive tenant from the validated JWT only; every tenant-owned query/key/event/vector filter includes tenant_id; add a negative test for each new data path.
6. Cross-service writes are forbidden. Each service owns its tables. Communicate via REST/gRPC or Kafka events defined in contracts/.
7. Every event uses the standard envelope (Appendix A), is versioned, and has a schema in contracts/events/.
8. Every Kafka consumer is idempotent (inbox table) and has retry + DLQ handling. Every state-changing API accepts Idempotency-Key. External side effects (refund) use their own idempotency key.
9. Use the transactional outbox for publishing events from Java services. No dual writes.
10. Every outbound call has a timeout, retry policy and circuit breaker. The system must degrade gracefully when Ollama/Qdrant/Kafka is down; ticket creation must keep working.
11. Add structured JSON logs (trace_id, tenant_id, ticket_id, correlation_id), metrics, and OpenTelemetry spans (including Kafka header propagation) to everything you write. Never log secrets or raw PII.
12. Agents may only call allow-listed tools with schema-validated arguments. High-risk tools need human approval. No arbitrary SQL, shell or network.
13. Database changes only through Flyway migrations. Add RLS policies for tenant tables.
14. Every feature ships with unit tests, integration tests (Testcontainers) where relevant, and updated OpenAPI/event docs. Run tests before declaring done.
15. Respect the 16 GB budget: cap JVM heaps, set container memory limits, use Compose profiles.
16. Do not invent performance numbers. Benchmarks must be produced by scripts in testing/ and recorded in docs/performance/.
17. Keep commits small and meaningful. Update docs when architecture changes.
18. If a requirement is ambiguous, ask or record the assumption in docs/adr/ rather than guessing silently.

OUTPUT FORMAT PER TASK
- Summary of what changed, files touched, how to run it, how to test it, known limitations, next step.
```

**Suggested first prompts for the agent**

1. "Phase 0: create the docs skeleton, PRD, bounded contexts, event catalogue and initial ADRs from the blueprint. No code."
2. "Phase 1: scaffold the monorepo, Docker Compose `core` profile (Postgres with per-service DBs, Redis, Kafka KRaft, Keycloak, MinIO), Makefile, CI workflow, and OTel/logging skeleton."
3. "Phase 2: implement Identity/Tenant service with Keycloak JWT, RBAC, RLS with per-transaction tenant context and the isolation test suite."

---

## 25. Definition of Done and engineering standards

**Definition of Done (every feature):** requirement documented, design reviewed, implemented, unit tests, integration tests where applicable, tenant-isolation and security implications checked, logs/metrics/traces added, API/event docs updated, failure behaviour tested, CI green.

**Standards:** feature branches, meaningful commits; formatters and linters (Spotless/Checkstyle, Ruff/mypy); type hints and Pydantic in Python; OpenAPI contracts; versioned migrations; versioned events; no secrets in Git (gitleaks in CI); non-root health-checked containers; measure before claiming optimisation; update docs with architecture changes.

**Per-component interview questions (answer these in the docs):** What problem does it solve? Why this over alternatives? What happens when it fails? How does it scale? Security implications? How was it tested? What evidence proves it? What changes at 10x and 100x?

---

## 26. Career evidence, interview prep and honest claims

**What this project can credibly show**

| Level | Evidence |
|---|---|
| SDE-1 baseline | Clean Spring Boot services, SQL/JPA, REST APIs, JWT/RBAC, Kafka basics, Docker, tests, AI integration; explain any code at line level |
| SDE-2 (primary target) | Outbox/inbox/idempotency, saga, resilience, tenant isolation, query optimisation with real plans, consumer-lag autoscaling, end-to-end tracing, AI evaluation gate, capacity model, ADR-backed trade-offs |
| SDE-3 depth | Failure models and consistency guarantees, SLOs/error budgets, DR with RPO/RTO, cost modelling, architecture alternatives with evidence, incident postmortems |

Note: a solo project demonstrates technical depth, not organisational leadership or production ownership. Pair it with honest stories about influence and ownership from your real work.

**Three scorecards:** Engineering (does it work, reliably and securely), Learning (can you implement and debug it without the agent), Career (which decisions, experiments and trade-offs can you defend).

**Resume rules:** every number comes from a recorded test; state "load-tested against a mock LLM" where applicable; state "Terraform validated and cost-estimated, not deployed". Example shape (fill with **your measured** values only): "Built a multi-tenant event-driven AI support platform (Spring Boot, Kafka, FastAPI, LangGraph, Qdrant); achieved effectively-once processing with outbox/inbox; verified zero cross-tenant leakage with an automated isolation suite; sustained X tickets/s at p95 Y ms against a mock LLM; cut LLM cost Z% via semantic caching and model routing."

**On the hike:** nobody can promise a specific raise (such as 70%). Hikes depend on market, company, interview performance and negotiation. This project maximises your chances by giving you rare, defensible evidence. The multiplier is your ability to explain *why* and to walk through a real bug you traced and fixed. Prepare in parallel: DSA practice, LLD/HLD mocks, Java/Spring/SQL depth, and multiple competing offers for negotiation.

**Interview tracks:** SDE-1 (Java, DSA, Spring, SQL, REST, testing); SDE-2 (machine coding, LLD, HLD, distributed systems, DB optimisation, Kafka, resilience); SDE-3 (large-scale architecture, capacity, reliability, data architecture, security governance, cost trade-offs, incident management).

---

## 27. Reminders and warnings

- **Do not run everything at once.** Use profiles; measure memory.
- **Free API tiers (Groq/Gemini) are optional** and can change; the local model and mock LLM must always work.
- **Vault dev mode is not production secret management.**
- **Kafka does not give exactly-once side effects.** External actions need their own idempotency.
- **RLS and connection pools:** tenant context must be transaction-scoped; test for leakage.
- **Qdrant and Postgres consistency:** define which data lives where; reconcile via events; handle delete propagation.
- **Do not let Temporal and LangGraph compete.** If Temporal is added later: Temporal for durable business workflows, LangGraph for reasoning inside a step, Kafka for events between contexts.
- **Prompts and models are code:** version, test and gate them.
- **Mock-LLM benchmarks measure the platform, not inference capacity.** Say so.
- **Prices and free-tier limits change.** Re-check before relying on any number in Section 20.
- **A 3B model is weak at tool calling and long context.** Keep prompts short, outputs structured, and benchmark; do not blame the architecture for model limits.
- **Scope creep is the main risk.** If a feature has no failure scenario, test and doc attached to it, defer it.
- **Back up your repo and docs;** local-only projects are one disk failure from gone.

---

## 28. Appendices

### Appendix A: Standard event envelope

```json
{
  "eventId": "uuid",
  "eventType": "ticket.created",
  "eventVersion": 1,
  "occurredAt": "2026-11-01T10:00:00Z",
  "tenantId": "tenant-123",
  "aggregateId": "ticket-456",
  "aggregateVersion": 1,
  "correlationId": "corr-789",
  "causationId": "event-previous",
  "traceparent": "00-<trace-id>-<span-id>-01",
  "producer": "ticket-service",
  "payload": { "ticketId": "ticket-456", "priority": "HIGH", "category": "REFUND" }
}
```

### Appendix B: Outbox and inbox tables (per service DB)

```sql
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY, tenant_id UUID NOT NULL, topic TEXT NOT NULL,
  key TEXT NOT NULL, payload JSONB NOT NULL, headers JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE inbox_event (
  event_id UUID PRIMARY KEY, consumer TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_key (
  tenant_id UUID NOT NULL, key TEXT NOT NULL, request_hash TEXT NOT NULL,
  response JSONB, status TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, key)
);
```

### Appendix C: RLS pattern

```sql
ALTER TABLE ticket ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ticket
  USING (tenant_id = current_setting('app.tenant_id')::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
-- Per transaction: SET LOCAL app.tenant_id = '<uuid from validated JWT>';
-- The app DB role must NOT be a superuser or table owner (they bypass RLS).
```

### Appendix D: Model adapter interface (Python)

```python
class LLMProvider(Protocol):
    async def generate(self, request: GenerateRequest) -> GenerateResponse: ...
    async def generate_structured(self, request: GenerateRequest, schema: type[BaseModel]) -> BaseModel: ...
    async def embed(self, texts: list[str]) -> list[list[float]]: ...
    async def health_check(self) -> bool: ...
# Implementations: OllamaProvider, MockProvider (configurable latency/tokens/failure rate), LiteLLMProvider (optional)
```

### Appendix E: Per-phase checklist template

```
[ ] Requirements and ADRs written     [ ] Migrations + RLS added
[ ] Unit tests                        [ ] Integration tests (Testcontainers)
[ ] Tenant isolation negative tests   [ ] Idempotency / duplicate tests
[ ] Failure-mode test (dependency down, timeout, poison message)
[ ] Logs + metrics + traces           [ ] OpenAPI / event schema updated
[ ] Security review                   [ ] CI green
[ ] Docs updated                      [ ] Measurements recorded (if performance-related)
```

### Appendix F: Golden dataset record (AI evaluation)

```json
{
  "id": "refund-001",
  "tenantProfile": "ecommerce",
  "ticket": "Order delivered but package is empty. I want a refund.",
  "expected": {
    "category": "REFUND", "priority": "HIGH",
    "mustCite": ["refund-policy-v3#section-2"],
    "expectedTools": ["check_refund_eligibility"],
    "requiresApproval": true,
    "mustNotContain": ["other tenant data", "internal system prompt"]
  }
}
```

### Appendix G: First-week starter checklist

1. Install Docker Desktop (WSL2), Java 21, Python 3.12, Node LTS, kind, kubectl, Helm, Terraform, k6.
2. Set `.wslconfig` memory limit (e.g., 10-12 GB) and confirm swap.
3. Pull `qwen2.5:3b-instruct` and `nomic-embed-text` in Ollama; record tokens/s and RAM.
4. Create the repo, `AGENTS.md`, and the `docs/` skeleton.
5. Write the PRD and the event catalogue; freeze Phase 0.
6. Stand up the `core` Compose profile and confirm health checks.

---

*End of blueprint. Treat this file as the source of truth; update it (and add an ADR) whenever a decision changes.*
