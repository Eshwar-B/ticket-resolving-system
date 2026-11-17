# Chapter 5: Database Architecture and Data Flow

---

## 5.1 Polyglot Persistence & Database-Per-Service Topology

The Enterprise AI Platform strictly enforces the **Database-per-Service** architectural pattern to guarantee bounded contexts, prevent distributed database deadlocks, and eliminate accidental cross-domain joins. Rather than utilizing a single monolithic relational database where all services read and write to shared tables, the platform partitions storage across dedicated relational, vector, cache, and object datastores.

```
+----------------------------------------------------------------------------------------------------+
|                                 POLYGLOT PERSISTENCE LAYER                                        |
+----------------------------------------------------------------------------------------------------+
|  RELATIONAL (PostgreSQL 16)         |  VECTOR (Qdrant)     |  IN-MEMORY (Redis 7)  |  OBJECT (MinIO) |
|  - identity_db (Keycloak auth)      |  - kb_chunks         |  - Rate Limits        |  - Attachments  |
|  - ticket_db (Lifecycle & Outbox)   |    (768-dim nomic)   |  - Token Buckets      |  - Raw Ingest   |
|  - knowledge_db (FTS metadata)      |  - tenant_id payload |  - Agent State Cache  |  - Audio/Export |
|  - workflow_db (Sagas & Tasks)      |                      |  - JWT Blacklist      |                 |
|  - notification_db (Outbox/Inbox)   |                      |                       |                 |
|  - usage_db (Token quotas)          |                      |                       |                 |
|  - audit_db (Immutable compliance)  |                      |                       |                 |
+----------------------------------------------------------------------------------------------------+
```

### The Metaphor: The Bank Deposit Box Vault
Imagine a high-security international private bank:
- **PostgreSQL Databases:** Each customer segment has their own dedicated vault room (Tickets, Knowledge, Workflow). The key to one vault never opens another.
- **Row-Level Security (RLS):** Inside a vault room, every safety deposit box is locked with a biometric scanner that only recognizes the specific tenant company (`app.tenant_id`). Even if a bank teller is inside the room, they cannot open a box belonging to Corporation B when acting on behalf of Corporation A.
- **Qdrant:** The bank's forensic image and fingerprint index. It doesn't store money or transaction ledgers; it stores mathematical spatial coordinates (embeddings) to find matches in milliseconds.
- **Redis:** The teller’s scratchpad and rapid cash counter. If someone makes 10 withdrawals in 5 seconds, the scratchpad blocks them instantly before they even reach the vault.
- **MinIO:** The cold storage warehouse in the basement where bulky physical files, original signed paper documents, and customer package photos are boxed and cataloged.

---

## 5.2 Relational Data Isolation: PostgreSQL Row-Level Security (RLS)

In a multi-tenant enterprise system, software bugs in SQL queries (such as forgetting a `WHERE tenant_id = ?` clause) are the #1 cause of catastrophic data leakage. The platform completely neutralizes this class of vulnerability at the **database engine level** using PostgreSQL Row-Level Security.

### 5.2.1 How RLS Operates at Runtime

```
                    +------------------------------------+
                    |   Incoming Request with JWT        |
                    +------------------------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  Spring Boot / Filter              |
                    |  Extracts tenant_id: 1111-2222     |
                    +------------------------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  HikariCP Connection Acquired      |
                    +------------------------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  EXECUTE:                          |
                    |  SET LOCAL app.tenant_id =         |
                    |  '1111-2222-3333-4444';            |
                    +------------------------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  Hibernate runs business query:    |
                    |  SELECT * FROM tickets;            |
                    +------------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------+
|  PostgreSQL Engine Internal Evaluation:                                |
|  FILTER: (tenant_id = current_setting('app.tenant_id')::uuid)         |
|  Result: Only rows belonging to Tenant 1111-2222 are returned!        |
+-----------------------------------------------------------------------+
```

### 5.2.2 The Flyway RLS Migration Pattern

Every table containing tenant-scoped data is created with RLS enabled by default:

```sql
-- Flyway Migration V1__create_tickets_table.sql

CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(64),
    assigned_agent_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- 1. Enable RLS on the table
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;

-- 2. Force RLS for all users (including table owner)
ALTER TABLE tickets FORCE ROW LEVEL SECURITY;

-- 3. Create Tenant Isolation Policy
CREATE POLICY tenant_isolation_policy ON tickets
    AS RESTRICTIVE
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 4. Create composite performance indexes
CREATE INDEX idx_tickets_tenant_status ON tickets(tenant_id, status);
CREATE INDEX idx_tickets_tenant_customer ON tickets(tenant_id, customer_id);
CREATE INDEX idx_tickets_created_at ON tickets(created_at DESC);
```

### 5.2.3 Connection Interceptor Implementation

In Spring Boot, every database interaction executes within an interceptor or transaction boundary that sets the session variable:

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
```

```java
@Component
public class HibernateTenantAspect {
    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void setSessionTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
                }
            });
        }
    }
}
```

---

## 5.3 Detailed Database Schemas and Tables

### 5.3.1 Service 1: `ticket_db` (Ticket Lifecycle Domain)

| Table Name | Primary Key | Critical Columns | Foreign Keys / References | Description |
|---|---|---|---|---|
| `tickets` | `id` (UUID) | `tenant_id`, `customer_id`, `title`, `description`, `status`, `priority`, `category`, `assigned_agent_id`, `resolved_at` | RLS isolation on `tenant_id` | Core ticket record holding current lifecycle state and metadata |
| `ticket_events` | `id` (UUID) | `ticket_id`, `tenant_id`, `event_type`, `payload` (JSONB), `created_at`, `actor_id` | FK `ticket_id` -> `tickets(id)` | Event log tracking every state mutation, comment, triage action, and internal note |
| `outbox_events` | `id` (UUID) | `aggregate_type`, `aggregate_id`, `event_type`, `payload` (TEXT/JSON), `created_at`, `processed` (BOOLEAN) | None (Polymorphic) | Transactional outbox buffer for zero-loss Kafka publishing |

```
                        +----------------------+
                        |       tickets        |
                        +----------------------+
                        | PK  id (UUID)        |
                        |     tenant_id (UUID) |<-----+
                        |     title (VARCHAR)  |      |
                        |     status (VARCHAR) |      |
                        +----------------------+      |
                                   | 1                | RLS Isolation
                                   |                  | Enforcement
                                   | N                |
                        +----------------------+      |
                        |    ticket_events     |      |
                        +----------------------+      |
                        | PK  id (UUID)        |      |
                        | FK  ticket_id (UUID) |      |
                        |     tenant_id (UUID) |<-----+
                        |     event_type       |
                        |     payload (JSONB)  |
                        +----------------------+
```

### 5.3.2 Service 2: `knowledge_db` (Knowledge & Document Store)

| Table Name | Primary Key | Critical Columns | Foreign Keys / References | Description |
|---|---|---|---|---|
| `knowledge_documents` | `id` (UUID) | `tenant_id`, `title`, `source_type` (PDF, FAQ, POLICY), `content_hash`, `status` (PENDING, INDEXED, FAILED), `minio_path` | RLS isolation on `tenant_id` | Tracks ingested documents, upload metadata, and MinIO object storage locations |
| `knowledge_chunks` | `id` (UUID) | `document_id`, `tenant_id`, `chunk_index`, `content` (TEXT), `tsv` (TSVECTOR for PostgreSQL Full-Text Search), `qdrant_point_id` (UUID) | FK `document_id` -> `knowledge_documents(id)` | Stores parsed document chunks, full-text inverted index tsvectors, and Qdrant vector links |
| `outbox_events` | `id` (UUID) | `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `processed` | None | Publishes document indexing and deletion events to Kafka |

### 5.3.3 Service 3: `workflow_db` (Saga & Human-in-the-Loop Orchestration)

| Table Name | Primary Key | Critical Columns | Foreign Keys / References | Description |
|---|---|---|---|---|
| `workflow_definitions` | `id` (UUID) | `tenant_id`, `name`, `version`, `steps_config` (JSONB), `is_active` | RLS isolation on `tenant_id` | Defines deterministic workflow graphs (e.g. Refund Verification, Tier-2 Escalation) |
| `workflow_instances` | `id` (UUID) | `tenant_id`, `definition_id`, `ticket_id`, `current_state`, `context_data` (JSONB), `status` (RUNNING, PAUSED, COMPLETED, FAILED) | FK `definition_id` -> `workflow_definitions(id)` | Active saga state machine execution record |
| `workflow_tasks` | `id` (UUID) | `instance_id`, `tenant_id`, `task_type` (AI_EVAL, HUMAN_APPROVAL), `assigned_role`, `decision` (APPROVED, REJECTED), `resolved_by` | FK `instance_id` -> `workflow_instances(id)` | Pending human approval gates or AI tool execution checkpoints |
| `outbox_events` | `id` (UUID) | `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `processed` | None | Emits saga state transitions and resolution events |

### 5.3.4 Service 4: `notification_db` (Notification & Webhooks)

| Table Name | Primary Key | Critical Columns | Foreign Keys / References | Description |
|---|---|---|---|---|
| `notifications` | `id` (UUID) | `tenant_id`, `recipient_id`, `channel` (EMAIL, SMS, IN_APP, WEBHOOK), `subject`, `body`, `status` (QUEUED, SENT, FAILED), `sent_at` | RLS on `tenant_id` | Audit trail of all customer-facing alerts, email receipts, and internal notices |
| `inbox_events` | `id` (UUID) | `event_id` (VARCHAR), `topic`, `payload`, `received_at`, `processed_at` | None (Unique index on `event_id`) | Idempotent consumer deduplication inbox |

### 5.3.5 Service 5 & 6: `usage_db` and `audit_db` (FinOps & Compliance)

| Database | Table Name | Critical Columns | Description |
|---|---|---|---|
| `usage_db` | `token_usage_records` | `id`, `tenant_id`, `ticket_id`, `model_name`, `prompt_tokens`, `completion_tokens`, `estimated_cost_usd`, `timestamp` | Granular per-request token consumption accounting for FinOps budget enforcement |
| `usage_db` | `tenant_quotas` | `tenant_id`, `monthly_token_budget`, `tokens_consumed_current_month`, `max_concurrency` | Tenant subscription level quotas and rate ceiling gates |
| `audit_db` | `audit_logs` | `id`, `tenant_id`, `actor_id`, `action`, `resource_type`, `resource_id`, `client_ip`, `user_agent`, `payload` (JSONB), `created_at` | Append-only, tamper-evident audit record for SOC2 / ISO 27001 regulatory compliance |

---

## 5.4 Vector Database: Qdrant Collection Design

The platform uses **Qdrant** for high-dimensional vector similarity retrieval. Qdrant operates as a standalone engine running on host port `6333`.

```
+-------------------------------------------------------------------------+
|                         QDRANT VECTOR COLLECTION                        |
|                         Collection: "kb_chunks"                         |
+-------------------------------------------------------------------------+
|  Vector Config:                                                         |
|  - Dimension: 768 (nomic-embed-text)                                    |
|  - Distance Metric: Cosine                                              |
|  - Index: HNSW (Hierarchical Navigable Small World, M=16, ef_construct=100) |
+-------------------------------------------------------------------------+
|  Payload Structure:                                                     |
|  {                                                                      |
|    "tenant_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",                 |
|    "document_id": "e4b2d8f1-9c8a-4d22-b5f7-6b1e8a9d0c2e",               |
|    "chunk_id": "0a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",                 |
|    "title": "Return & Refund Policy v2.1",                              |
|    "source_type": "PDF",                                                |
|    "category": "Billing",                                               |
|    "content": "Customers are eligible for an empty package refund..."   |
|  }                                                                      |
+-------------------------------------------------------------------------+
|  Payload Filter Index:                                                  |
|  - Keyword Index on "tenant_id" (Guarantees zero cross-tenant leakage)  |
|  - Keyword Index on "category"                                          |
+-------------------------------------------------------------------------+
```

### The Filtered Vector Search Query

To prevent an agent from ever seeing chunks from another company, all vector searches execute with an absolute payload filter:

```json
POST /collections/kb_chunks/points/search
{
  "filter": {
    "must": [
      {
        "key": "tenant_id",
        "match": {
          "value": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        }
      }
    ]
  },
  "params": {
    "hnsw_ef": 64,
    "exact": false
  },
  "vector": [0.0124, -0.0458, 0.0891, ..., -0.0211],
  "limit": 5,
  "with_payload": true
}
```

---

## 5.5 In-Memory Caching & Key Namespacing in Redis

Redis 7 operates as the platform’s high-throughput, low-latency in-memory state engine. To avoid key collisions and guarantee multi-tenant segregation, all keys follow a strict hierarchical naming convention:

```
t:{tenant_id}:{subsystem}:{identifier}
```

### Key Prefix Catalog

| Pattern | Data Type | TTL | Purpose |
|---|---|---|---|
| `t:{tid}:ratelimit:{ip_or_user}` | Hash / Sorted Set | 60 sec | Leaky bucket rate limiting counter per user/tenant |
| `t:{tid}:session:{jwt_jti}` | String / BitMap | Expiry of JWT | Active session metadata and token blacklist revocation check |
| `t:{tid}:agent_state:{ticket_id}` | JSON / String | 24 hours | Ephemeral LangGraph execution checkpoint across multi-step turns |
| `t:{tid}:kb_cache:{query_hash}` | String | 15 mins | Semantic cache for repeated knowledge base queries |
| `t:{tid}:quota:monthly_tokens` | Atomic Integer | End of Month | Instantaneous token usage accumulator before flushing to PostgreSQL |

---

## 5.6 MinIO Object Storage Architecture

Unstructured files (ticket attachments, customer receipt PDFs, photos of damaged goods, voice memos, and ingested policy documents) are never stored as database BLOBs. Storing binary assets in relational tables degrades database buffer pools, bloats backups, and degrades query performance.

Instead, they are streamed to **MinIO S3-compatible Object Storage**:

```
minio-bucket/
├── tenants/
│   ├── tenant-uuid-alpha/
│   │   ├── raw-documents/
│   │   │   └── policy-2024.pdf
│   │   └── attachments/
│   │       └── ticket-4491/
│   │           ├── damaged_item_photo.jpg
│   │           └── shipping_receipt.pdf
│   └── tenant-uuid-beta/
│       └── raw-documents/
│           └── company_handbook.docx
```

### Access Control Protocol:
1. Client requests an attachment upload URL via `ticket-service`.
2. `ticket-service` generates an S3 **Presigned PUT URL** with a 15-minute expiration, scoped strictly to `tenants/{tenant_id}/attachments/{ticket_id}/{uuid}.ext`.
3. Client uploads directly to MinIO; backend compute nodes never experience memory exhaustion handling large binary streams.
4. When downloading, `ticket-service` generates a short-lived **Presigned GET URL** only after confirming the authenticated user belongs to the owning `tenant_id`.

---

## 5.7 Database Migrations with Flyway

All database schemas are version-controlled using **Flyway**. Schema definitions reside inside each Java microservice repository under `src/main/resources/db/migration/`:

```
ticket-service/src/main/resources/db/migration/
├── V1__init_ticket_schema.sql         # Base tables: tickets, ticket_events
├── V2__enable_rls_and_policies.sql    # RLS policies and role assignments
├── V3__create_outbox_table.sql        # Transactional outbox table
└── V4__add_ticket_category_index.sql  # Performance tuning indexes
```

### Strict Migration Rules:
1. **Never alter an applied migration:** Once merged, migration files are immutable. Schema changes require a new monotonic version (`V5__...`).
2. **Backward-Compatible Schema Evolution:** All column additions must be `NULLABLE` or define a `DEFAULT` value to ensure zero-downtime rolling upgrades.
3. **Automated Verification:** In CI pipelines, Flyway runs against fresh test containers before unit and integration test execution.

---

## 5.8 Data Flow Across Microservices (End-to-End Tracing)

```
[Customer]
    │ 1. POST /api/v1/tickets (Create Ticket)
    ▼
[API Gateway] ──(Validates JWT & forwards X-Tenant-ID)──► [Ticket Service]
                                                               │
                                                               ├─► 2. BEGIN DB TX
                                                               │    SET LOCAL app.tenant_id = '...'
                                                               │    INSERT INTO tickets (...)
                                                               │    INSERT INTO ticket_events (...)
                                                               │    INSERT INTO outbox_events (...)
                                                               │   COMMIT DB TX
                                                               │
                                              [Kafka Producer Poller]
                                                               │ 3. Polls outbox_events
                                                               ▼
                                              [Kafka: ticket.events.v1]
                                                               │
                           ┌───────────────────────────────────┼───────────────────────────────────┐
                           │ (Async Event Consumption)         │                                   │
                           ▼                                   ▼                                   ▼
                   [AI Orchestrator]                  [Workflow Service]                 [Notification Service]
                           │                                   │                                   │
                           ├─► Hybrid RAG Search               ├─► Creates Workflow Instance       ├─► Checks inbox_events
                           │    - Postgres FTS (knowledge_db)  │    INSERT INTO workflow_instances │   (Deduplication)
                           │    - Vector Search (Qdrant)       │    (Saga orchestration)           │   Sends confirmation email
                           │                                   │                                   │   INSERT INTO notifications
                           ├─► Calls Ollama LLM                ▼                                   ▼
                           ▼                               [workflow_db]                       [notification_db]
                   [Emits ticket.resolved]
```

This decoupled data flow ensures that even if the AI Orchestrator or Notification Service is temporarily offline, the ticket creation transaction is safely committed in `ticket_db` and guaranteed to be processed when downstream systems recover.
