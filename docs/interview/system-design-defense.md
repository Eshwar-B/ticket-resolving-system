# System Design Defense & Architectural Trade-Offs

This document defends the key architectural decisions of the Enterprise AI Platform against common alternatives during technical system design reviews and engineering defenses.

---

## 1. Trade-Off 1: PostgreSQL Row-Level Security vs Schema-Per-Tenant

### The Question:
*"Why not create a separate PostgreSQL schema for each tenant (`tenant_01`, `tenant_02`), or a separate database per tenant?"*

### Defense:
1. **Connection Pool Scalability:** With database-per-tenant, each tenant requires a dedicated connection pool. Under 100 tenants with HikariCP min-connections of 10, the database immediately exhausts connection limits (1000+ connections), consuming excessive RAM.
2. **Schema Migration Sprawl:** With schema-per-tenant, applying a Flyway migration across 5,000 tenants requires running 5,000 migrations sequentially, creating massive operational drift risk during rolling updates.
3. **Kernel Enforcement with Shared Schema:** PostgreSQL Row-Level Security provides kernel-level isolation with a single unified schema and shared connection pool. Even if an application developer forgets `WHERE tenant_id = ?`, the PostgreSQL kernel automatically applies the tenant filter from `app.tenant_id`.

---

## 2. Trade-Off 2: Synchronous Ingestion + Asynchronous AI vs Synchronous AI

### The Question:
*"Why not call the AI model synchronously when the user clicks 'Submit Ticket'?"*

### Defense:
1. **High Availability SLA:** External or local LLM inference is stochastic and high-latency (500ms to 5000ms+). If Ollama crashes or experiences GPU contention, synchronous ticket creation would fail, dropping customer tickets.
2. **Zero Dependency on AI for Core Business:** By design, ticket creation is 100% synchronous and fast (p95 < 50ms) writing to PostgreSQL and Outbox. AI triage, RAG retrieval, and resolution happen asynchronously via Kafka workers. If AI is offline, tickets still enter the system cleanly and can be handled manually.

---

## 3. Trade-Off 3: Transactional Outbox vs Dual-Writes

### The Question:
*"Why not save to Postgres and publish to Kafka directly in the Spring Boot service method?"*

### Defense:
1. **The Dual-Write Hazard:** In distributed systems, writing to a database and an event bus without distributed transactions results in inconsistency whenever a network partition or crash occurs between the two calls.
2. **Atomic Outbox Guarantee:** Writing the ticket and outbox event in the same ACID transaction guarantees at-least-once delivery to Kafka via the poller. Idempotent consumer inboxes guarantee exactly-once processing semantics downstream.

---

## 4. Trade-Off 4: Local-First Open-Source ($0 Spend) vs Cloud APIs

### The Question:
*"Why use local Ollama, Qdrant, MinIO, and Kafka KRaft instead of OpenAI, Pinecone, AWS S3, and Amazon MSK?"*

### Defense:
1. **Total Data Sovereignty & Zero API Spend:** Enterprise data (PII, customer financial tickets) never leaves the host infrastructure. Zero billable third-party API dependencies.
2. **Reproducibility & Hardware Discipline:** Demonstrates the ability to engineer high-throughput event-driven microservices within strict local constraints (16 GB RAM ceiling, container memory caps <= 384MiB).
