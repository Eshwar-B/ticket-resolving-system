# Chapter 3: Technology Stack Justification & Trade-Off Matrix

Every technology in the Enterprise AI Platform was selected based on strict engineering criteria: **zero licensing costs (₹0), local execution capability, memory efficiency within 16 GB RAM, and proven enterprise production stability.**

---

## 3.1 Java 21 & Spring Boot 3.3.4 (Control Plane Core)

* **What it is:** The modern Long-Term Support (LTS) release of the Java platform combined with the premier enterprise microservices framework.
* **Why it was selected:**
  * **Virtual Threads (Project Loom):** Enables lightweight, high-concurrency request handling without thread-pool starvation.
  * **Deterministic Memory & HikariCP:** Provides battle-tested database connection pooling and predictable heap behavior within `-Xmx256m` to `-Xmx384m` container caps.
  * **Spring Data JPA & Hibernate 6:** Offers powerful object-relational mapping, lifecycle callbacks, and schema validation.
* **Where it is used:** Powers all control plane microservices (`ticket-service`, `knowledge-service`, `workflow-service`, `notification-service`, `usage-audit-service`, and `common`).
* **Communication:** Listens for HTTP REST on ports 8081–8085; connects to PostgreSQL via JDBC; publishes and consumes Kafka records via Spring Kafka.
* **Alternatives Considered:**
  * *Node.js / Express:* Rejected due to lack of compile-time type safety, weak ORM transaction control, and single-threaded CPU bottlenecks during high-concurrency outbox processing.
  * *Go (Golang):* Highly performant, but lacks the mature enterprise ecosystem of Spring (e.g., Flyway, Spring Security, Hibernate lifecycle intercepts, Resilience4j).
* **Real-World Analogy:** A reinforced concrete foundation of a skyscraper—rigid, unshakeable, and strictly engineered to withstand massive structural loads.

---

## 3.2 PostgreSQL 16 & Flyway (Relational Storage & Schema Migrations)

* **What it is:** The world's most advanced open-source object-relational database system, paired with an automated database migration engine.
* **Why it was selected:**
  * **Native Row-Level Security (RLS):** Allows defining security policies directly in the database engine (`USING (tenant_id = current_setting('app.tenant_id', true))`), guaranteeing that data leakage is impossible even if an application bug omits a `WHERE` clause.
  * **Full-Text Search (tsvector & GIN Indexing):** Built-in linguistic text processing (stemming, lexeme matching) enables hybrid RAG search without requiring a separate Elasticsearch cluster.
  * **ACID Transactions:** Guarantees that ticket creation and outbox event recording commit atomically.
* **Where it is used:** Serves as the persistent source of truth across 7 isolated logical databases (`ticket_db`, `knowledge_db`, `workflow_db`, `notification_db`, `usage_db`, `identity_db`, `keycloak_db`).
* **Communication:** Accessed via JDBC port 5432 using non-superuser credentials (`app_user`).
* **Alternatives Considered:**
  * *MySQL 8:* Rejected because MySQL lacks native PostgreSQL-style Row-Level Security session variables and has inferior full-text search and JSONB indexing capabilities.
  * *MongoDB:* Rejected because multi-document ACID transactions across outbox and business collections introduce heavy performance penalties and lack kernel-enforced multi-tenant isolation policies.
* **Real-World Analogy:** A master vault with biometric locks on every single safety deposit box.

---

## 3.3 Apache Kafka 3.8 in KRaft Mode (Distributed Event Streaming)

* **What it is:** A distributed event streaming platform used for high-throughput, fault-tolerant publish-subscribe pipelines.
* **Why it was selected:**
  * **KRaft Mode (No ZooKeeper):** Replaces the legacy, resource-heavy ZooKeeper dependency with an internal Raft consensus controller, dramatically slashing memory usage and operational complexity on local workstations.
  * **Tenant Partition Ordering:** By setting `tenantId` as the record partition key, Kafka guarantees strict FIFO ordering for all events belonging to a single organization while parallelizing across tenants.
  * **Consumer Lag & Replayability:** If the AI Plane crashes, events remain safely buffered on the Kafka commit log and resume exactly where processing stopped.
* **Where it is used:** Streams domain events across `ticket.events.v1`, `workflow.events.v1`, and `usage.events.v1`.
* **Communication:** Broker port 9092 (internal port 29092) communicating via the binary Kafka protocol.
* **Alternatives Considered:**
  * *RabbitMQ:* Excellent AMQP broker, but lacks a persistent, replayable commit log. Re-running historical events for AI evaluation or audit audits is trivial in Kafka but complex in RabbitMQ.
  * *Redis Pub/Sub:* Fire-and-forget messaging with zero persistence. If an AI consumer is down when a message is sent, the event is permanently lost.
* **Real-World Analogy:** A high-speed pneumatic tube system connecting all departments, where every capsule is stamped with an indelible serial number and logged in a ledger.

---

## 3.4 Redis 7 (Distributed Caching & In-Memory Rate Limiting)

* **What it is:** An ultra-fast, in-memory key-value data store supporting rich data structures with TTL eviction.
* **Why it was selected:**
  * **Sub-Millisecond Latency:** Evaluates sliding-window rate limit counters (`t:{tenant_id}:rl:{endpoint}:{window}`) without adding database read overhead.
  * **Semantic Query Caching:** Buffers identical LLM resolution plans and RAG results, saving expensive embedding computations and inference cycles.
  * **Tenant Key Namespacing:** Enforces hard key prefixes (`t:{tenant_id}:...`) to guarantee zero cross-tenant memory leakage.
* **Where it is used:** Caches rate-limiting sliding windows and AI Plane semantic resolutions.
* **Communication:** TCP port 6379 with password authentication (`redis_dev_password`).
* **Alternatives Considered:**
  * *Memcached:* Simple and fast, but lacks complex data structures (hashes, sets, sorted sets) and key eviction notifications.
  * *Local In-Process Java Maps (Caffeine):* Fast, but does not synchronize state across multiple horizontal instances of microservices.
* **Real-World Analogy:** The whiteboard on an executive's office wall where short-term reminders, quick tallies, and scratchpad calculations are wiped clean every hour.

---

## 3.5 Python 3.12 & FastAPI (AI Intelligence Plane)

* **What it is:** A high-performance, asynchronous web framework based on standard Python type hints and Pydantic v2.
* **Why it was selected:**
  * **Ecosystem Compatibility:** Native integration with LangGraph, PyTorch, NumPy, and modern AI evaluation frameworks.
  * **Asynchronous Concurrency (uvloop & asyncio):** Handles non-blocking HTTP tool invocations and event subscriptions efficiently.
  * **Pydantic v2 Validation:** High-speed C-based data validation ensuring all AI tool parameters and event schemas conform strictly to system contracts.
* **Where it is used:** Powers the `ai-plane` microservice running on port 8000.
* **Communication:** Consumes from Kafka; calls Java REST endpoints via `httpx`; queried by internal operators via REST.
* **Alternatives Considered:**
  * *Flask:* Older synchronous framework lacking native async/await performance and modern Pydantic validation.
  * *Spring AI (Java):* Rapidly emerging, but significantly behind the Python LangGraph ecosystem in agentic graph workflows, multi-agent state machines, and fine-grained token context compression.
* **Real-World Analogy:** A specialist research laboratory staffed by multilingual data scientists working alongside the corporate legal team.

---

## 3.6 LangGraph & LangChain Pattern (Agentic Orchestration)

* **What it is:** A library for building stateful, multi-actor applications with LLMs, modeling agent interactions as cyclical graphs.
* **Why it was selected:**
  * **Cyclical State Machine Architecture:** Unlike simple linear chains (DAGs), real-world problem resolution requires loops (e.g., Triage -> Retrieve -> Resolve -> Critic -> If safe, Execute; Else, Retry or Escalate).
  * **Deterministic Guardrails:** The supervisor controls agent transitions based on explicit programmatic conditions rather than unpredictable LLM whims.
* **Where it is used:** Coordinates the Triage, Retrieval, Resolution, and Critic agents in `ai-plane/app/supervisor/supervisor_graph.py`.
* **Alternatives Considered:**
  * *AutoGPT / CrewAI:* Too autonomous and non-deterministic; high risk of infinite execution loops and unexpected API costs.
  * *Standard LangChain Chains:* Strictly linear (DAG); unable to implement sophisticated Human-in-the-Loop pause-and-resume workflows.
* **Real-World Analogy:** An air traffic control flowchart where planes (requests) must follow rigid runway checkpoints before landing.

---

## 3.7 Qdrant Vector Database (Dense Vector Similarity Search)

* **What it is:** An open-source vector similarity search engine and vector database written in Rust.
* **Why it was selected:**
  * **Rust-Powered Memory Efficiency:** Runs smoothly within a **384 MB container memory cap**, crucial for our 16 GB hardware constraint.
  * **Hard Payload Filtering:** Supports pre-filtering by `tenant_id` at the index level, ensuring zero vector leakage across organizations.
  * **Rich Metadata & Cosine Distance:** Stores chunk indices, document IDs, and version numbers directly alongside 384-dimensional dense vectors.
* **Where it is used:** Stores dense semantic embeddings of knowledge base documents in collection `enterprise_knowledge`.
* **Communication:** REST API on port 6333 and gRPC on port 6334.
* **Alternatives Considered:**
  * *Pinecone:* Proprietary paid SaaS API; completely violates the ₹0 cloud spend invariant.
  * *pgvector (PostgreSQL extension):* Excellent, but running heavy HNSW vector graph calculations inside the primary transaction database competes with transactional I/O. Separating vector search into Qdrant protects database connection pools.
  * *Milvus:* Feature-rich, but requires heavy microservice dependencies (etcd, MinIO, Pulsar) that exceed our 16 GB RAM laptop budget.
* **Real-World Analogy:** A semantic library indexing system where books are arranged not by author name, but by the conceptual meaning of their paragraphs.

---

## 3.8 MinIO & S3Mock (Object Storage for Knowledge Documents)

* **What it is:** High-performance, S3-compatible object storage for unstructured files.
* **Why it was selected:**
  * **S3 API Standardization:** Uses standard AWS S3 SDK protocols, allowing seamless migration to AWS S3, Google Cloud Storage, or MinIO in production without rewriting application code.
  * **Tenant Path Isolation:** Enforces storage keys structured as `tenants/{tenantId}/documents/{documentId}/v{version}/{filename}`.
  * **Lightweight Local Footprint:** `adobe/s3mock:latest` runs in under 110 MB RAM, simulating complete bucket and object lifecycles locally.
* **Where it is used:** Stores raw uploaded PDFs, policy handbooks, and customer attachments.
* **Communication:** S3 REST protocol on port 9000 (container port 9090).
* **Alternatives Considered:**
  * *Local Filesystem (`java.io.File`):* Creates brittle microservice coupling; files on one container cannot be accessed by another container without fragile shared volume mounts.
* **Real-World Analogy:** An automated document archive warehouse where physical legal contracts are vacuum-sealed in labeled, fireproof containers.

---

## 3.9 Keycloak 24 (Identity & Access Management)

* **What it is:** An open-source Identity and Access Management (IAM) solution implementing OpenID Connect (OIDC) and OAuth 2.0.
* **Why it was selected:**
  * **Cryptographic Token Verification:** Issues RSA/EC signed JWTs that microservices verify statelessly using public keys from the JWKS endpoint.
  * **Custom Token Claims:** Maps tenant memberships into the JWT payload (`tenant_id`), establishing the single source of truth for all downstream authorizations.
  * **Realm & RBAC Management:** Configures granular roles (`ADMIN`, `AGENT`, `CUSTOMER`) across isolated organizational realms.
* **Where it is used:** Provides single sign-on (SSO) and authentication on port 8080.
* **Communication:** HTTP OIDC endpoints backed by `keycloak_db` in PostgreSQL.
* **Alternatives Considered:**
  * *Auth0 / Okta:* Paid proprietary SaaS services that violate the ₹0 cost constraint.
  * *Custom Database Passwords (bcrypt in `ticket_db`):* Antipattern; mixes identity concerns into domain ticketing and forces stateful session management.
* **Real-World Analogy:** The national passport office that issues tamper-proof, holographic passports recognized by all international border guards.

---

## 3.10 React 18, Vite & TypeScript (Operator Admin Console)

* **What it is:** A modern, typed component-based frontend single-page application (SPA) bundled with Vite.
* **Why it was selected:**
  * **Fast Developer Experience (HMR):** Vite provides sub-second hot module replacement.
  * **TypeScript Type Safety:** Shares interface definitions with backend DTOs (`Ticket`, `ApprovalRequest`, `DocumentMeta`, `AuditRecord`), preventing runtime property errors.
  * **Lightweight Footprint:** Requires zero container overhead during backend verification runs; runs locally via `npm run dev`.
* **Where it is used:** Provides human managers and support agents with the administrative interface in `apps/admin-console/` (port 3000).
* **Communication:** Calls backend REST endpoints via browser `fetch` using dynamic `X-Tenant-Id` headers.
* **Real-World Analogy:** The mission control dashboard in a space launch facility displaying engine temperatures, fuel gauges, and override switches.
