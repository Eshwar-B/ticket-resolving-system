# Chapter 1: Project Introduction & Problem Statement

## 1.1 Project Overview

The **Enterprise AI Platform** is an enterprise-grade, local-first, zero-trust multi-tenant support ticket resolution and workflow automation system. It bridges the critical reliability gap between mission-critical enterprise systems and autonomous Generative AI.

At its core, the platform operates across two synchronized application planes:
1. **Deterministic Java 21 Control Plane (Spring Boot 3.3.4):** Enforces transaction ACID boundaries, PostgreSQL kernel-level Row-Level Security (RLS), state machine integrity, and distributed outbox events.
2. **Autonomous Python 3.12 AI Intelligence Plane (FastAPI & LangGraph):** Orchestrates multi-agent triage, hybrid semantic retrieval, policy grounding, and Human-in-the-Loop approval workflows.

The platform guarantees strict **zero-trust multi-tenancy** across every architectural layer—relational storage, in-memory caches, vector search indices, event streaming topics, and object storage—while running on a strict **₹0 cloud cost model** within a **16 GB host RAM budget**.

---

## 1.2 The Real-World Analogy: The International Airport Cargo & Customs Hub

To explain this platform to anyone from a non-technical stakeholder to an executive architect, use the analogy of a **High-Security International Airport Cargo Terminal**:

* **The Customer Ticket:** Represents an incoming international freight shipment arriving at the terminal gate.
* **The Spring Cloud Gateway:** Acts as the **Terminal Security Gate & Passport Control**. It checks the cargo manifest (Keycloak JWT), verifies corporate identity (Tenant ID), and blocks unauthorized or rate-exceeded trucks before they ever enter the facility.
* **The Core Ticket Service (Control Plane):** Acts as the **Customs Manifest Registry**. It assigns an official, immutable tracking barcode (UUID/Ticket Number) and logs the arrival into the permanent customs ledger before any inspection begins.
* **The Transactional Outbox:** Acts as the **Certified Dispatch Register**. The manifest clerk writes down the shipment details and puts an entry into the physical dispatch box in the *exact same motion*. If the clerk drops their pen or faints, neither record exists. Once recorded, an internal courier (Outbox Poller) delivers the manifest to the sorting floor.
* **Apache Kafka (KRaft):** Acts as the **Terminal Conveyor Belt System**. Parcels are sorted into color-coded chutes according to the airline/tenant partition key. Crashing an inspection station does not stop the belts; messages wait reliably in line.
* **The Python AI Intelligence Plane (LangGraph Supervisor):** Acts as the **Automated Cargo Sorting & Inspection Team**:
  * **Triage Agent:** Scans the box label, categorizing whether it contains electronics, sensitive chemicals, or perishable goods.
  * **Retrieval Agent (Hybrid RAG):** Acts as the **Legal Research Clerk**. Runs to the library archives to pull the exact national customs tariff regulation and policy page.
  * **Resolution Agent:** Formulates the customs clearance recommendation based strictly on the retrieved rulebook.
  * **Critic Agent:** Acts as the **Quality Inspector** who checks whether the resolution agent actually cited the real policy handbook or made up an unauthorized rule.
* **The Workflow Approvals Engine (Human-in-the-Loop):** If the shipment is high-value or hazardous (e.g., a refund request over the automated limit), the robotic arms pause. A human Senior Customs Supervisor must inspect the package and sign the physical release form before the cargo leaves the gate.
* **PostgreSQL Row-Level Security (RLS):** Acts as **Biometric Keycard Vaults**. Even if an employee from Airline B enters the warehouse, their keycard physically cannot unlock the doors to Airline A's cargo lockers.

---

## 1.3 Problem Statement & Industry Context

Modern enterprise customer support organizations face a severe structural dilemma when attempting to adopt Generative AI:

1. **The Brittle Wrapper Antipattern:** Most enterprise AI implementations are shallow wrappers around commercial LLM APIs. When the third-party LLM experiences outages or latency spikes, core ticketing operations grind to a complete halt.
2. **Cross-Tenant Vector Data Leakage:** Traditional multi-tenant applications often store vector embeddings in shared indices without database-enforced row filtering, leading to catastrophic security breaches where Customer A's sensitive corporate data surfaces in Customer B's AI responses.
3. **Dual-Write Inconsistency & Silent Failure:** Naive microservices attempt to write to a local database and immediately publish to a message broker in two separate network calls. If the broker is unreachable, the ticket exists in the database but is lost forever to the asynchronous AI pipeline.
4. **Hallucination & Financial Liability:** Autonomous LLMs frequently fabricate policies, invent return windows, or promise unauthorized financial refunds without cryptographic audit trails or human supervisory gates.
5. **Runaway Cloud Costs:** Enterprise architectures frequently assume unlimited cloud budgets, running high-cost proprietary APIs (e.g., OpenAI, Pinecone, AWS DynamoDB) that scale exponentially with volume.

---

## 1.4 The Proposed Solution

The Enterprise AI Platform resolves these systemic flaws through rigorous architectural invariants:
* **Asymmetric Plane Decoupling:** Core ticketing is completely isolated from the AI plane. If Ollama or Qdrant crashes, support agents and customers can continue creating, assigning, and updating tickets without interruption.
* **Dual-Write Elimination via Transactional Outbox:** Every state mutation commits the domain entity and an `outbox_events` record in a single atomic database transaction, polled and relayed to Kafka with guaranteed at-least-once delivery.
* **Kernel-Level Multi-Tenancy:** PostgreSQL Row-Level Security (`SET LOCAL app.tenant_id = ?`) enforces isolation at the database engine level, neutralizing SQL injection and ORM mapping flaws.
* **Hybrid RAG with Reciprocal Rank Fusion (RRF):** Fuses dense vector semantic retrieval (Qdrant) with sparse keyword full-text search (PostgreSQL tsvector) to provide cited, hallucination-proof context (`[Doc: "Title" v1 #chunk-0]`).
* **Deterministic LangGraph Supervision & HITL:** Safe procedural requests are resolved autonomously; all irreversible, financial, or high-risk actions (e.g., refunds) are routed to a human approval queue.
* **100% Open-Source Local Stack (₹0 Cost):** Uses Kafka KRaft, PostgreSQL 16, Redis 7, Keycloak 24, Qdrant, MinIO, and Ollama, running entirely within a 16 GB RAM developer workstation envelope.

---

## 1.5 Target User Personas

| Persona | Role | Primary Responsibilities & Permissions |
|---|---|---|
| **System Administrator** | DevOps / Platform Engineer | Manages infrastructure containers, Kafka cluster health, database schemas, and global quotas. |
| **Tenant Administrator**| Corporate Organization Manager | Configures tenant-specific SLA rules, auto-refund ceilings, uploaded knowledge policies, and team memberships. |
| **Support Agent / Lead** | Triage & Resolution Operator | Reviews assigned tickets, handles human escalations, inspects AI drafts, and executes approved refunds. |
| **End Customer** | External Ticket Submitter | Submits tickets, reads public responses, and tracks resolution progress. Zero access to internal notes or other tenants' data. |
| **AI Supervisor** | Autonomous Background Agent | Consumes Kafka events, performs RAG retrieval, drafts cited responses, and requests human approvals. |

---

## 1.6 Functional & Non-Functional Requirements

### Functional Requirements (FR)
- **FR-01: Multi-Tenant Ticketing Lifecycle:** Full CRUD operations across an 8-state deterministic machine (`NEW`, `OPEN`, `IN_PROGRESS`, `WAITING_FOR_CUSTOMER`, `WAITING_FOR_APPROVAL`, `PENDING`, `RESOLVED`, `CLOSED`).
- **FR-02: Public vs. Internal Commenting:** Strict demarcation between customer-visible responses and internal audit notes.
- **FR-03: SLA Tracking & Breach Escalation:** Automated computation of first-response and resolution deadlines with scheduled background breach detection.
- **FR-04: Knowledge Ingestion & Chunking:** Multi-tenant document ingestion, PDF/text parsing, recursive chunking, and dual-index storage (S3 + Qdrant + PostgreSQL).
- **FR-05: Hybrid RAG Search:** Dense vector similarity combined with sparse tsvector full-text search via Reciprocal Rank Fusion (RRF).
- **FR-06: Human-in-the-Loop Approvals:** Dedicated workflow state engine managing `PENDING`, `APPROVED`, and `REJECTED` states for high-risk actions.
- **FR-07: In-App Alerts & Webhook Subscriptions:** Signed HMAC-SHA256 webhook dispatch and user notification feeds.
- **FR-08: Append-Only Audit Logging:** Immutable compliance trail capturing all entity mutations and AI decisions.

### Non-Functional Requirements (NFR)
- **NFR-01: Zero-Trust Isolation:** Cross-tenant read/write operations must result in an immediate HTTP 404 or database-level denial. No tenant identifier passed in client headers can override validated JWT claims.
- **NFR-02: Local Hardware Discipline:** Total memory consumption across all active microservices and databases must remain strictly within **16 GB RAM** (~2 GB measured during typical execution).
- **NFR-03: Zero Cloud Expenditure (₹0):** Critical path operations must not require paid cloud APIs or subscription services.
- **NFR-04: Fault Tolerance & Decoupling:** Complete outage of Ollama, Qdrant, or the AI plane must have 0% impact on core ticket creation and human resolution.
- **NFR-05: Idempotency:** Duplicate mutation API calls carrying the same `Idempotency-Key` must return identical cached responses without side-effects.

---

## 1.7 Current Implementation Baseline

As of November 2026, **Phases 1 through 8 are 100% complete, verified, and committed to Git (`origin/main`)**:
* **Phase 1:** Monorepo scaffolding, Docker Compose multi-profile orchestration, PostgreSQL 16 multi-DB initialization, Redis 7, Kafka KRaft.
* **Phase 2:** Keycloak OIDC integration, server-side JWT claims parsing, PostgreSQL Row-Level Security (RLS), Redis tenant key prefixes.
* **Phase 3:** Core Ticket Service (Spring Boot 3.3.4), 6 domain entities, 8-state deterministic machine, RFC 7807 problem details, AOP RLS interceptor.
* **Phase 4:** Event-driven reliability, transactional outbox engine, 1-second background relay poller, Kafka KRaft streaming, distributed idempotency engine.
* **Phase 5:** Knowledge Service (Spring Boot), S3Mock object storage, Qdrant vector database, PostgreSQL tsvector full-text search, Reciprocal Rank Fusion, exact citations.
* **Phase 6:** Workflow Service (Human Approvals engine), LangGraph Multi-Agent resolution plane (Triage, Retrieval, Resolution, Critic, Supervisor).
* **Phase 7:** AI Platform Governance, Model Adapter interface (Mock + Ollama), Prompt & Workflow Versioning (v1/v2), Golden Dataset offline evaluation suite, CI regression gates, Semantic Caching, Token context compression.
* **Phase 8:** Notification Service (Port 8084), Usage & Audit Service (Port 8085), Redis sliding window rate limiting, scheduled SLA breach detector, and React 18 / TypeScript Operator Admin Console.
