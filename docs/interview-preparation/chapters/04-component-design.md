# Chapter 4: Detailed Component & Microservice Design

This chapter dives into the internal architecture, classes, database interactions, and event flows of every implemented microservice.

---

## 4.1 Ticket Service (`backend/ticket-service`)

### 1. Purpose & Responsibilities
The **Ticket Service** (Port `8081`) is the core transaction engine of the platform. It manages the entire lifecycle of customer tickets, enforce state transitions, captures comments, calculates SLA deadlines, tracks agent assignments, and guarantees event delivery via the Transactional Outbox pattern.

### 2. Internal Architecture & Layering
```
[ HTTP Ingress ] ──► TenantContextFilter (Extract X-Tenant-Id & X-User-Id)
                            │
                            ▼
                     TicketController (Spring MVC REST)
                            │
                            ▼
                  TenantTransactionInterceptor (AOP @Around @Transactional)
                     - Session.doWork(set_config('app.tenant_id', ...))
                            │
                            ▼
                       TicketService (Domain Logic & State Machine)
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
        TicketRepository       OutboxEventRepository
                 │                     │
                 └──────────┬──────────┘
                            ▼
                PostgreSQL 16 (ticket_db)
```

### 3. Key Classes & Modules
* **`Ticket.java`:** JPA entity mapping to `tickets` table with optimistic locking, SLA relationship, assignments list, and comment collections.
* **`TicketStateMachine.java`:** Pure domain state machine enforcing valid transitions across 8 states:
  * `NEW` -> `OPEN` or `CANCELLED`
  * `OPEN` -> `IN_PROGRESS`, `WAITING_FOR_CUSTOMER`, `WAITING_FOR_APPROVAL`, or `PENDING`
  * `IN_PROGRESS` -> `RESOLVED`, `WAITING_FOR_CUSTOMER`, or `PENDING`
  * `PENDING` -> `RESOLVED`, `OPEN`, or `CLOSED`
  * `RESOLVED` -> `CLOSED` or `OPEN` (Customer Reopen)
* **`OutboxPoller.java`:** `@Scheduled(fixedDelayString = "1000")` worker querying `outbox_events WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED` and publishing to Kafka topic `ticket.events.v1`.
* **`IdempotencyFilter.java`:** Intercepts mutation requests with `Idempotency-Key`, caches response payloads in `idempotency_records`, and returns cached `201 Created` with `X-Idempotent-Replay: true`.
* **`SlaBreachChecker.java`:** Background task querying active tickets past their `resolution_due` date and emitting `ticket.sla.breached.v1`.

### 4. REST Endpoints
* `POST /api/v1/tickets`: Creates a new ticket. Triggers atomic outbox insert.
* `GET /api/v1/tickets`: Returns cursor-paginated tickets scoped strictly to the authenticated tenant.
* `GET /api/v1/tickets/{id}`: Returns complete ticket details, comment history, and SLA status.
* `PATCH /api/v1/tickets/{id}/status`: Executes a state transition. Rejects invalid transitions with HTTP `422 Unprocessable Entity` (RFC 7807 Problem JSON).
* `POST /api/v1/tickets/{id}/comments`: Adds a `PUBLIC` or `INTERNAL` comment.

### 5. Security & Isolation
Uses `TenantTransactionInterceptor` to execute `SELECT set_config('app.tenant_id', ?, true)` on the physical JDBC connection before Hibernate flushes SQL queries.

### 6. Analogy
The central docket registry of a federal court—recording every motion, filing, and verdict with immutable chronological accuracy.

---

## 4.2 Knowledge Service (`backend/knowledge-service`)

### 1. Purpose & Responsibilities
The **Knowledge Service** (Port `8082`) manages organizational policy documents, versioning, recursive chunking, object storage in S3Mock, full-text keyword indexing in PostgreSQL, and dense vector synchronization in Qdrant.

### 2. Key Classes & Modules
* **`Document.java` & `DocumentChunk.java`:** JPA entities mapping documents and chunks. `DocumentChunk` includes a PostgreSQL generated column `tsv` (`tsvector`) indexed with a GIN index (`idx_chunks_tsv`).
* **`QdrantVectorService.java`:** Client wrapper for Qdrant REST API (`http://qdrant:6333`). Upserts 384-dimensional dense vectors with payload metadata (`tenant_id`, `document_id`, `chunk_index`). Enforces mandatory `tenant_id` filter on every search.
* **`KnowledgeService.java`:** Coordinates dual-index ingestion and computes Reciprocal Rank Fusion (RRF) across keyword and vector search results. Generates traceable citations: `[Doc: "Title" v<version> #chunk-<id>]`.

### 3. REST Endpoints
* `POST /api/v1/knowledge/upload`: Ingests a text or PDF document, archives the file in S3Mock, chunks the text, and synchronizes both PostgreSQL and Qdrant.
* `GET /api/v1/knowledge/documents`: Lists all indexed documents for the tenant.
* `POST /api/v1/knowledge/search`: Executes Hybrid RAG search combining dense cosine vectors and sparse tsvector full-text search.
* `DELETE /api/v1/knowledge/documents/{id}`: Soft-deletes document (`deleted_at = NOW()`), immediately purging it from search results.

### 4. Analogy
The master research library of an intelligence agency—cataloging every document by both exact keywords and overarching conceptual meaning.

---

## 4.3 Workflow Service (`backend/workflow-service`)

### 1. Purpose & Responsibilities
The **Workflow Service** (Port `8083`) governs sensitive, financial, and policy-restricted actions. When an AI agent recommends a refund or compensation, it must submit an approval request to this engine.

### 2. Key Classes & Modules
* **`ApprovalRequest.java`:** Entity capturing `ticket_id`, `action_type`, `payload`, `requester`, `approver_id`, and status (`PENDING`, `APPROVED`, `REJECTED`).
* **`WorkflowService.java`:** Enforces single-decision semantics. Re-approving an already approved or rejected request is rejected with HTTP `409 Conflict`.
* **`WorkflowController.java`:** Exposes management endpoints for operators. Publishes events to Kafka topic `workflow.events.v1` (`workflow.approval.granted.v1`, `workflow.approval.denied.v1`).

### 3. REST Endpoints
* `POST /api/v1/approvals`: Initiates a new approval request (called by AI Supervisor).
* `GET /api/v1/approvals/pending`: Returns queue of awaiting requests for human managers.
* `POST /api/v1/approvals/{id}/decision`: Executes an approval or rejection with manager audit notes.
* `GET /api/v1/approvals/ticket/{ticketId}`: Retrieves full approval history for a ticket.

### 4. Analogy
The dual-key authorization system for a corporate bank wire transfer—preventing automated software from dispersing funds without human oversight.

---

## 4.4 Notification Service (`backend/notification-service`)

### 1. Purpose & Responsibilities
The **Notification Service** (Port `8084`) delivers user alerts and signed webhooks to external enterprise systems (Slack, PagerDuty, CRM webhooks).

### 2. Key Classes & Modules
* **`Notification.java`:** Entity for in-app user notifications with read status and timestamps.
* **`WebhookSubscription.java`:** Entity storing registered external webhook URLs, subscribed event types, and SHA-256 hashed HMAC signing secrets.
* **`WebhookDelivery.java`:** Audit log of outbound delivery attempts, HTTP status codes, and retries.
* **`NotificationService.java`:** Handles registration, HMAC secret hashing, and notification reads.

### 3. REST Endpoints
* `POST /api/v1/notifications`: Creates an in-app alert.
* `GET /api/v1/notifications?userId={id}`: Returns paginated user notifications.
* `PATCH /api/v1/notifications/{id}/read`: Marks notification as read.
* `POST /api/v1/webhooks`: Registers a new webhook subscription.
* `GET /api/v1/webhooks`: Lists active webhook endpoints.
* `DELETE /api/v1/webhooks/{id}`: Deactivates a webhook.

### 4. Analogy
The certified mail and courier dispatch room—formatting outgoing notices and collecting signed proof-of-delivery receipts.

---

## 4.5 Usage & Audit Service (`backend/usage-audit-service`)

### 1. Purpose & Responsibilities
The **Usage & Audit Service** (Port `8085`) provides an immutable compliance trail and resource metering engine. It records audit events, tracks AI token consumption, and evaluates tenant quotas.

### 2. Key Classes & Modules
* **`AuditEvent.java`:** Immutable entity capturing actor, event type, resource ID, trace ID, and JSON payload. Updates and deletes are prohibited.
* **`UsageRecord.java`:** Captures service name, operation, quantity (tokens/calls), unit, and cost estimate.
* **`TenantQuota.java`:** Stores monthly caps for tickets, AI inferences, and vector storage.
* **`UsageAuditService.java`:** Computes usage aggregates by service and evaluates quota consumption.

### 3. REST Endpoints
* `POST /api/v1/audit`: Appends an audit event to the compliance log.
* `GET /api/v1/audit`: Returns paginated audit trail filtered by tenant.
* `POST /api/v1/usage`: Records a resource usage consumption record.
* `GET /api/v1/usage`: Returns usage summary aggregated by microservice.
* `GET /api/v1/quotas`: Returns tenant quota status and limits.

### 4. Analogy
The black box flight recorder of an aircraft—continuously recording instrument telemetry and cockpit decisions in an immutable, tamper-evident log.

---

## 4.6 Python AI Intelligence Plane (`ai-plane`)

### 1. Purpose & Responsibilities
The **AI Intelligence Plane** (Port `8000`) is the cognitive engine of the platform. Built on FastAPI, Pydantic v2, and LangGraph, it coordinates autonomous multi-agent reasoning, RAG context assembly, and safe tool execution.

### 2. Internal Agent Topology
```
           [ Kafka ticket.created.v1 ]
                        │
                        ▼
             ┌─────────────────────┐
             │    Triage Agent     │ ── Classifies Intent & Checks Financial Risk
             └──────────┬──────────┘
                        │
                        ▼
             ┌─────────────────────┐
             │   Retrieval Agent   │ ── Queries Knowledge Service Hybrid RAG
             └──────────┬──────────┘
                        │
                        ▼
             ┌─────────────────────┐
             │  Resolution Agent   │ ── Synthesizes Plan & Drafts Cited Response
             └──────────┬──────────┘
                        │
                        ▼
             ┌─────────────────────┐
             │    Critic Agent     │ ── Enforces Citation Grounding & Zero Trust
             └──────────┬──────────┘
                        │
       Safe? ───────────┴─────────── Financial / Irreversible?
         │                                       │
         ▼ (Direct Resolve)                      ▼ (Human Approval)
Update Ticket -> RESOLVED              Request Approval -> PENDING
```

### 3. Key Agent Modules
* **`triage_agent.py`:** Uses keyword and regex pattern matching to classify intent (`RETURN_REFUND`, `ACCOUNT_ACCESS`, `TECHNICAL_ISSUE`, `GENERAL_INQUIRY`) and detect financial keywords (`refund`, `chargeback`, `payout`, `reimburse`).
* **`retrieval_agent.py`:** Calls the Knowledge Service search API with tenant headers and extracts chunks and citations.
* **`resolution_agent.py`:** Formulates the public response text and internal audit notes. If financial action is detected, sets plan to `HUMAN_APPROVAL_REQUIRED`.
* **`critic_agent.py`:** Validates that citations exist and follow the exact format `[Doc: "Title" v<version> #chunk-<id>]`. Blocks responses that fabricate policies.
* **`supervisor_graph.py`:** Executes the state machine, manages context token budgets via `ContextManager`, checks `SemanticCache`, and emits operational telemetry to `usage.events.v1`.

### 4. Model Adapter & Governance Layer
* **`base_adapter.py`:** Abstract interface for `generate`, `generate_structured`, `embed`, and `health_check`.
* **`mock_adapter.py`:** Deterministic mock producing 384-dimensional embeddings and canned responses for zero-dependency local testing.
* **`ollama_adapter.py`:** Connects to local Ollama on port 11434 with automatic fallback to mock adapter.
* **`prompt_registry.py`:** Versioned prompt templates (`v1`, `v2`).
* **`version_store.py`:** Supports dynamic activation and rollback of workflow configurations.

### 5. Analogy
An autonomous surgical consultation panel—the triage nurse categorizes the patient, the medical librarian pulls the clinical studies, the attending physician drafts the treatment plan, and the chief medical officer reviews the plan for safety before any surgery begins.
