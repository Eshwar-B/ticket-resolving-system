# Chapter 2: High-Level System Architecture & Design Philosophy

## 2.1 The Dual-Plane Architectural Pattern

The defining architectural characteristic of the Enterprise AI Platform is the **strict physical decoupling between the Deterministic Control Plane and the Autonomous AI Intelligence Plane**.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENT & EDGE INGRESS                                │
│          React 18 Admin Console (:3000)  /  External REST Clients  /  Mobile App       │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ HTTP (JWT Bearer Token + X-Tenant-Id)
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        SPRING CLOUD GATEWAY / KEYCLOAK OIDC (:8080)                     │
│               - RSA/EC JWT Signature Verification    - Server-Side Tenant Derivation   │
│               - Distributed Rate Limiting (Redis)    - Ingress Header Sanitization     │
└───────────────────┬────────────────────────────────────────────────────────────────────┘
                    │
         ┌──────────┴──────────────────────────────────────────────────────┐
         ▼ Synchronous REST (Spring MVC)                                   │
┌─────────────────────────────────────────────────┐                        │
│       JAVA 21 DETERMINISTIC CONTROL PLANE       │                        │
│                                                 │                        │
│  ┌──────────────────┐    ┌───────────────────┐  │                        │
│  │  Ticket Service  │    │ Knowledge Service │  │                        │
│  │     (:8081)      │    │      (:8082)      │  │                        │
│  └────────┬─────────┘    └─────────┬─────────┘  │                        │
│           │                        │            │                        │
│  ┌────────┴─────────┐    ┌─────────┴─────────┐  │                        │
│  │ Workflow Service │    │ Notification Svc  │  │                        │
│  │     (:8083)      │    │      (:8084)      │  │                        │
│  └────────┬─────────┘    └─────────┬─────────┘  │                        │
│           │                        │            │                        │
│  ┌────────┴─────────┐                          │                        │
│  │ Usage/Audit Svc  │                          │                        │
│  │     (:8085)      │                          │                        │
│  └──────────────────┘                          │                        │
└───────────┬─────────────────────────────────────┘                        │
            │ Atomic Outbox Commit                                         │
            ▼                                                              │
┌───────────────────────────┐                                              │
│  POSTGRESQL 16 (Multi-DB) │                                              │
│  - Kernel RLS Isolation   │                                              │
│  - outbox_events table    │                                              │
└───────────┬───────────────┘                                              │
            │ Outbox Poller Relay (1s Fixed Delay)                         │
            ▼                                                              │
┌─────────────────────────────────────────────────┐                        │
│           APACHE KAFKA 3.8 (KRaft Mode)         │                        │
│  - ticket.events.v1 (Partitioned by tenantId)   │                        │
│  - workflow.events.v1                           │                        │
│  - usage.events.v1                              │                        │
└───────────┬─────────────────────────────────────┘                        │
            │ Event Streaming Subscription                                 │
            ▼                                                              │
┌─────────────────────────────────────────────────┐                        │
│        PYTHON 3.12 AI INTELLIGENCE PLANE        │◄───────────────────────┘
│                 (FastAPI :8000)                 │ Direct Trigger / Evaluation API
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │   LangGraph Multi-Agent Supervisor Loop   │  │
│  │   - Triage Agent (Intent & Urgency)       │  │
│  │   - Retrieval Agent (Hybrid RAG Client)   │  │
│  │   - Resolution Agent (Grounded Synthesis) │  │
│  │   - Critic Agent (Safety & Citations)     │  │
│  └─────────────────────┬─────────────────────┘  │
│                        │                        │
│  ┌─────────────────────┴─────────────────────┐  │
│  │ Model Adapter Layer (Ollama / Mock LLM)   │  │
│  │ Semantic Cache & Context Manager          │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### Why Separate Control Plane and AI Plane?

In traditional software development, engineers often try to write everything in a single language. However, an Enterprise AI architecture has two fundamentally conflicting requirements:

1. **Transaction Integrity & Concurrency (Java/JVM):**
   * Customer tickets, financial state, outbox tables, and user permissions require rigorous ACID guarantees, strict thread-local isolation, type safety, deterministic garbage collection, and battle-tested database pooling (HikariCP).
   * Java 21 with Spring Boot provides unmatched stability, performance, and memory efficiency under heavy multi-tenant concurrency.
2. **AI Ecosystem & Dynamic Graph Orchestration (Python):**
   * The modern AI and ML ecosystem (LangGraph, PyTorch, Hugging Face, Qdrant SDK, NumPy) evolves at an extraordinary velocity in Python.
   * Attempting to run LangGraph state machines or custom token-level RAG rerankers directly in Java forces fragile JNI wrappers or severely outdated Java AI ports.

**The Solution:** The Java Control Plane owns all database state, outbox tables, and external client contracts. The Python AI Plane acts as an asynchronous analytical worker that reads events from Kafka, queries knowledge, formulates plans, and invokes authorized Java REST APIs using strongly-typed schemas.

---

## 2.2 Monolith vs. Microservices Trade-Off Analysis

| Architectural Dimension | Monolithic Architecture | Microservices (Our Platform) | Justification for This Platform |
|---|---|---|---|
| **Database Coupling** | Single shared database with cross-table foreign keys. | Database-per-service (Separate logical databases in PostgreSQL 16). | Prevents ticket domain developers from accidentally corrupting identity, knowledge chunks, or approval audit trails. |
| **Fault Isolation** | A crash in the AI inference loop crashes the entire support portal. | Hard failure boundaries. Ticket creation survives complete AI failure. | Core business continuity: Support tickets must continue being created and assigned even during AI downtime. |
| **Technology Specialization** | Forced single runtime across all domains. | Polyglot (Java 21 for Control, Python 3.12 for AI). | Leverages the strongest enterprise ecosystem for transactions and the richest ecosystem for AI. |
| **Operational Overhead** | Low (Single deployment unit). | Moderate (Multi-container orchestration via Docker Compose). | Solved completely through Docker Compose profiles (`core`, `ai`, `obs`, `tools`, `full`) and unified Makefile scripts. |
| **Scalability Bottlenecks**| Heavy AI model memory footprints crowd out HTTP connection pools. | Independent memory limits per container (e.g., 384 MB caps). | Strict adherence to the 16 GB developer workstation RAM budget. |

---

## 2.3 Synchronous vs. Asynchronous Communication Boundaries

The platform strictly segregates synchronous communication from asynchronous communication:

```
[ Client Request ] ────( Synchronous HTTP )────► [ Ticket Service ]
                                                        │
                                                        ▼ ( Atomic DB Transaction )
                                                 [ PostgreSQL DB ]
                                                 - tickets
                                                 - outbox_events
                                                        │
                                                        ▼ ( Async Poller: 1 sec )
                                                 [ Kafka Broker ]
                                                        │
                                                        ▼ ( Async Event Stream )
                                                 [ AI Plane Supervisor ]
```

### 1. Synchronous REST Boundaries (HTTP/JSON)
* **Client-to-Gateway:** Ingress API calls (`POST /api/v1/tickets`, `GET /api/v1/tickets`) require immediate confirmation and return RFC 7807 problem details or created entity IDs.
* **AI Tool Execution:** When the AI Plane needs to fetch ticket details (`GET /api/v1/tickets/{id}`) or request human approvals (`POST /api/v1/approvals`), it uses synchronous REST calls with short timeouts (3.0s) and strict tenant headers.
* **Knowledge Retrieval:** The AI Retrieval Agent queries the Knowledge Service via `POST /api/v1/knowledge/search` synchronously during prompt assembly.

### 2. Asynchronous Event Boundaries (Apache Kafka KRaft)
* **Ticket Lifecycle Events (`ticket.events.v1`):** When a ticket is created, updated, or breached, the state change is published asynchronously to Kafka via the Transactional Outbox.
* **Approval Decision Events (`workflow.events.v1`):** When a human manager approves or rejects a request, an event is emitted to notify downstream workers.
* **Telemetry & Metering (`usage.events.v1`):** AI token counts, execution latencies, and model names are streamed asynchronously to avoid adding latency to the user response path.

---

## 2.4 Database-per-Service Principle

The blueprint establishes an uncompromised rule: **Every domain service exclusively owns its data store.**

* **No Cross-Database Foreign Keys:** A ticket entity in `ticket_db` references `customer_id` as a UUID/string column, never as an active JPA relationship across databases.
* **No Cross-Service Queries:** The `ticket-service` never issues SQL queries against `knowledge_db` or `workflow_db`. All cross-service coordination occurs via REST contracts or Kafka events.
* **Independent Migration Versioning:** Each service maintains its own Flyway migration scripts (`V1__...sql`) in its resource directory, ensuring schemas evolve independently without coordination bottlenecks.

---

## 2.5 Detailed Walkthrough of the Complete System Architecture Diagram

Refer to diagram `docs/interview-preparation/diagrams/01-system-architecture.mmd` for visual mapping:

### 1. Client & Operator Layer
* **React 18 Admin Console (:3000):** Built with Vite, TypeScript, and Tailwind CSS. Provides operators with real-time visibility into active tickets, pending approvals, knowledge documents, and immutable audit logs.
* **End Customer Client:** Submits support requests through external portal integrations or direct REST APIs.

### 2. Ingress & Identity Layer
* **Spring Cloud Gateway (:8080):** Serves as the central reverse proxy. Intercepts incoming requests, validates JWT signatures against Keycloak, strips untrusted client-supplied tenant headers, and injects validated `X-Tenant-Id` and `X-User-Id` headers.
* **Keycloak OIDC Provider (:8080):** Manages organizations, realms, user credentials, and OAuth2/OIDC token generation with custom `tenant_id` claim mapping.

### 3. Java Control Plane
* **Ticket Service (:8081):** Manages ticket CRUD, customer records, SLA tracking, agent assignments, comments, and the transactional outbox table.
* **Knowledge Service (:8082):** Handles raw document uploads to S3Mock, text chunking, PostgreSQL tsvector keyword indexing, Qdrant vector synchronization, and Reciprocal Rank Fusion search.
* **Workflow Service (:8083):** Manages human approval requests for sensitive actions, tracking `PENDING`, `APPROVED`, and `REJECTED` state transitions.
* **Notification Service (:8084):** Handles in-app notifications and delivers signed HMAC-SHA256 webhooks to external endpoints.
* **Usage & Audit Service (:8085):** Maintains the append-only system audit log, records token usage metrics, and evaluates tenant resource quotas.

### 4. Python AI Intelligence Plane (:8000)
* **LangGraph Supervisor Loop:** Coordinates the multi-agent graph: Triage -> Retrieval -> Resolution -> Critic -> Action Execution.
* **Model Adapter Layer:** Abstract client interface with seamless fallback between local Ollama instances and zero-dependency mock adapters.
* **Semantic Cache:** Redis-backed query cache scoped by tenant (`t:{tenant_id}:cache:{hash}`) to eliminate duplicate LLM inference calls.

### 5. Infrastructure & Storage
* **PostgreSQL 16 (:5432):** Houses 7 logical databases protected by kernel-level Row-Level Security (`SET LOCAL app.tenant_id = ?`).
* **Redis 7 (:6379):** Backs sliding-window rate limiting, semantic response caching, and short-term conversation state.
* **Apache Kafka 3.8 KRaft (:9092):** Distributed message broker operating without ZooKeeper, partitioning all events by `tenantId`.
* **Qdrant Vector DB (:6333):** Stores 384-dimensional dense embeddings with mandatory payload filtering by `tenant_id`.
* **MinIO / S3Mock (:9000):** Stores raw uploaded documents under immutable tenant-prefixed object keys (`tenants/{tenant_id}/...`).
