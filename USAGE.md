# Enterprise AI Platform — Comprehensive Operational & User Guide (`USAGE.md`)

> **Authoritative Handbook for Platform Installation, Administration, User Personas, Feature Capabilities, and Operational Boundaries**  
> **Cost Model:** Strictly **₹0** (100% open-source local-first stack — zero paid cloud dependencies)  
> **Hardware Discipline:** Optimized for **16 GB RAM** (Windows 11 / Linux / macOS)  
> **Target Audience:** System Administrators, Tenant Administrators, Support Agents, Software Engineers, and Auditors  

---

## Table of Contents

1. [Executive Overview & Architecture](#1-executive-overview--architecture)
2. [Setup from Scratch on a New Device (Zero to Running)](#2-setup-from-scratch-on-a-new-device-zero-to-running)
   - [2.1 Prerequisites Checklist](#21-prerequisites-checklist)
   - [2.2 Port Allocation & Conflict Prevention](#22-port-allocation--conflict-prevention)
   - [2.3 Step-by-Step Installation Commands](#23-step-by-step-installation-commands)
   - [2.4 Container Health & Connectivity Verification](#24-container-health--connectivity-verification)
3. [User Personas & Role-Based Access Control (RBAC)](#3-user-personas--role-based-access-control-rbac)
   - [3.1 System Administrator (Platform DevOps & Security Engineer)](#31-system-administrator-platform-devops--security-engineer)
   - [3.2 Tenant Administrator (Organization Manager)](#32-tenant-administrator-organization-manager)
   - [3.3 Support Agent & Team Lead (Triage & Resolution Operator)](#33-support-agent--team-lead-triage--resolution-operator)
   - [3.4 End Customer / External Client (Ticket Submitter)](#34-end-customer--external-client-ticket-submitter)
   - [3.5 Autonomous AI Supervisor (Async Background Agent)](#35-autonomous-ai-supervisor-async-background-agent)
4. [Exhaustive Feature-by-Feature Operational Guide](#4-exhaustive-feature-by-feature-operational-guide)
   - [Feature 1: Cryptographic Multi-Tenancy & Zero-Trust RLS](#feature-1-cryptographic-multi-tenancy--zero-trust-rls)
   - [Feature 2: Core Ticketing Lifecycle Management](#feature-2-core-ticketing-lifecycle-management)
   - [Feature 3: Deterministic State Machine Enforcement](#feature-3-deterministic-state-machine-enforcement)
   - [Feature 4: Distributed Idempotency Engine (`Idempotency-Key`)](#feature-4-distributed-idempotency-engine-idempotency-key)
   - [Feature 5: Transactional Outbox & Event Streaming Relay](#feature-5-transactional-outbox--event-streaming-relay)
   - [Feature 6: Comments & Granular Visibility Control](#feature-6-comments--granular-visibility-control)
   - [Feature 7: SLA Tracking & Tier Calculation](#feature-7-sla-tracking--tier-calculation)
5. [System Invariants: "What CAN Be Done" vs. "What CANNOT Be Done"](#5-system-invariants-what-can-be-done-vs-what-cannot-be-done)
6. [Troubleshooting & Maintenance Playbook](#6-troubleshooting--maintenance-playbook)
7. [Automated Verification & Diagnostic Drills](#7-automated-verification--diagnostic-drills)

---

## 1. Executive Overview & Architecture

The **Enterprise AI Platform** is an enterprise-scale autonomous customer service and ticketing system designed with a strict physical separation of concerns:

- **Java 21 Control Plane (Spring Boot 3.3.4):** High-throughput, deterministic transaction management, RFC 7807 problem details, strict state machine transitions, and database kernel-enforced Row-Level Security (RLS).
- **Python AI Plane (FastAPI / Python 3.12 / LangGraph):** Asynchronous event consumption, Agentic supervisors, Tool calling, Hybrid RAG with Qdrant, Guardrails, and Model evaluation.
- **Core Infrastructure (Docker Compose):** PostgreSQL 16 (per-service logical databases), Redis 7 (caching, rate-limiting), and Apache Kafka (KRaft mode, no ZooKeeper).
- **Cost Discipline:** Runs entirely locally on developer laptops with **₹0 cloud API spend** and under an explicit **16 GB RAM total system budget**.

```
                                  [ Client Ingress ]
                                          │
                                          ▼
                      ┌────────────────────────────────────────┐
                      │    Spring Cloud Gateway (Port 8080)    │
                      │  - JWT Validation (Keycloak OIDC)      │
                      │  - Tenant Derivation (app_metadata)    │
                      │  - Distributed Rate Limiting (Redis)   │
                      └───────────────────┬────────────────────┘
                                          │
               ┌──────────────────────────┴──────────────────────────┐
               ▼                                                     ▼
┌───────────────────────────────┐                 ┌────────────────────────────────────┐
│   Java 21 Control Plane       │                 │     Python AI Intelligence Plane   │
│   (Spring Boot 3.3.4)         │                 │     (FastAPI / LangGraph / Qdrant) │
│                               │                 │                                    │
│  - Ticket Core Microservice   │                 │  - Multi-Agent Orchestration       │
│  - PostgreSQL RLS Enforcement │                 │  - Hybrid RAG (Dense + BM25)       │
│  - Strict State Machine       │                 │  - SLA & Categorization Predictor  │
│  - Transactional Outbox Relay │                 │  - Local LLM via Ollama (qwen2.5)  │
│  - Distributed Idempotency    │                 │  - PII Masking & Semantic Guard    │
└──────────────┬────────────────┘                 └──────────────────▲─────────────────┘
               │                                                     │
               ▼                                                     │
   ┌───────────────────────┐                             ┌───────────┴───────────┐
   │ PostgreSQL 16 (ticket)│                             │ Apache Kafka (KRaft)  │
   │ - RLS Protected       │                             │ - ticket.created.v1   │
   │ - atomic outbox_events│────────(1s Poller)─────────►│ - ticket.updated.v1   │
   │ - idempotency_records │                             │ - DLQ & Retry Topics  │
   └───────────────────────┘                             └───────────────────────┘
```

---

## 2. Setup from Scratch on a New Device (Zero to Running)

Follow these steps to bring up the complete platform on any brand-new Windows 11, Ubuntu Linux, or macOS machine.

### 2.1 Prerequisites Checklist

Before executing any commands, verify that your new machine meets the minimum tooling requirements:

1. **Operating System:** Windows 11 (build 22000+), Ubuntu Linux 22.04 LTS+, or macOS 13+ (Ventura).
2. **Hardware:** Minimum 4 CPU cores, 16 GB Physical RAM (at least 6 GB free for Docker).
3. **Docker Engine:** Docker Desktop (Windows/macOS) or Docker Engine + Docker Compose Plugin (Linux) v24.0+.
   - *Windows Note:* Ensure WSL2 backend is enabled in Docker Desktop settings.
4. **Git:** Git 2.30+ installed and configured on your system PATH.
5. **PowerShell 5.1+ (Windows) or Bash (Linux/macOS):** For running automation scripts.
6. *(Optional)* **Java Development Kit:** JDK 21+ (Eclipse Temurin) if modifying or compiling Java services locally outside Docker. If using Docker only, the containerized build runs automatically.

### 2.2 Port Allocation & Conflict Prevention

The platform binds several ports on `localhost`. Ensure no existing software (such as an existing local PostgreSQL, Redis, or Apache web server) is using these ports:

| Port | Service | Purpose | Protocol |
|---|---|---|---|
| `5432` | `enterprise-ai-postgres` | PostgreSQL 16 Database Server | TCP / JDBC |
| `6379` | `enterprise-ai-redis` | Redis 7 Caching & Rate Limiting | TCP / RESP |
| `8080` | `enterprise-ai-keycloak` | Keycloak Identity Provider (OIDC/OAuth2) | HTTP / REST |
| `8081` | `enterprise-ai-ticket-service` | Spring Boot Core Ticketing API | HTTP / REST |
| `9092` | `enterprise-ai-kafka` | Apache Kafka KRaft Broker | TCP / Kafka |

> [!TIP]
> To check if any port is currently occupied on Windows, run:
> ```powershell
> Test-NetConnection -ComputerName localhost -Port 5432
> ```
> If `TcpTestSucceeded : True`, stop your local service before proceeding.

### 2.3 Step-by-Step Installation Commands

#### Step 1: Clone the Repository
```powershell
git clone https://github.com/Eshwar-B/ticket-resolving-system.git
cd ticket-resolving-system
```

#### Step 2: Initialize Configuration
Copy the sample environment file to create your local `.env`:
```powershell
cp .env.example .env
```
*(The defaults in `.env.example` are pre-tuned for local zero-cloud ₹0 development with correct usernames, passwords, and memory limits).*

#### Step 3: Launch Core Infrastructure & Services
To start PostgreSQL, Redis, Kafka KRaft, Keycloak, and the Ticket Service:
```powershell
docker compose --profile core up -d --build
```
*Docker will:*
1. Initialize PostgreSQL with 7 isolated databases (`ticket_db`, `identity_db`, etc.).
2. Apply database schemas and RLS policies via Flyway.
3. Import the Keycloak realm configuration with pre-seeded demo tenants and users.
4. Build and start the `ticket-service` Spring Boot container with a strict 384 MB memory limit.

### 2.4 Container Health & Connectivity Verification

Wait 20–30 seconds for services to achieve full readiness, then verify status:

```powershell
docker compose ps
```

All core containers should report `healthy` or `running`:
- `enterprise-ai-postgres` (healthy)
- `enterprise-ai-redis` (healthy)
- `enterprise-ai-kafka` (healthy)
- `enterprise-ai-keycloak` (healthy)
- `enterprise-ai-ticket-service` (healthy/running)

Check the Spring Boot Actuator health endpoint:
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/actuator/health"
```
**Expected Output:**
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

---

## 3. User Personas & Role-Based Access Control (RBAC)

The platform enforces strict role boundaries. Every interaction is evaluated against the user's persona and tenant membership.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SYSTEM ADMINISTRATOR                            │
│  - Keycloak Realm Management       - Container Topology & Memory Caps  │
│  - Database Flyway Migrations      - System-Level Worker Maintenance   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Manages Tenants
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        TENANT ADMINISTRATOR                            │
│  - Manage Organization Users       - Define SLA Policies (Tiers)       │
│  - Customer Master Directory       - Audit Organization Ticket Metrics │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Manages Support Teams
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        SUPPORT AGENT / LEAD                            │
│  - Claim & Assign Tickets          - Progress Ticket State Machine     │
│  - Add Internal Notes & Public Sol - Review Escalation & SLA Due Dates │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Resolves Inquiries For
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        END CUSTOMER / CLIENT                           │
│  - Submit Inquiries (Idempotent)   - View Public Ticket Updates        │
│  - Provide Follow-up Details       - Reopen Resolved Issues (in grace) │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 3.1 System Administrator (Platform DevOps & Security Engineer)

The System Administrator oversees the global runtime, container boundaries, database migrations, and identity federation.

#### Primary Responsibilities:
- **Container Lifecycle:** Starting, stopping, and scaling Docker Compose profiles (`core`, `tools`, `ai`, `obs`, `full`).
- **Identity & Realms:** Managing the Keycloak instance on port `8080`, generating client secrets, and registering new tenant realms.
- **Database Schema Upgrades:** Applying Flyway SQL migrations across all logical databases (`ticket_db`, `identity_db`, etc.).
- **Kafka Topic Governance:** Creating partitions, configuring retention periods, and monitoring the Dead-Letter Queue (`ticket.dlq.v1`).

#### Allowed Operations (What CAN Be Done):
- Run maintenance scripts such as `scripts/dev.ps1` and automated test suites.
- Execute Flyway migrations: `docker compose --profile tools run flyway-ticket-db migrate`.
- Inspect container memory and CPU stats: `docker stats`.
- Trigger the Outbox Publisher background relay.

#### Forbidden Operations (What CANNOT Be Done):
- **Bypass Tenant Isolation via APIs:** The Administrator cannot view ticket data through application APIs without providing a valid tenant context. Cross-tenant data leakage is blocked by PostgreSQL Row-Level Security even for database users.
- **Deploy Paid Cloud Services:** System Administrators must adhere to the ₹0 cost policy. No AWS, GCP, Azure, or paid OpenAI API integrations are permitted.

---

### 3.2 Tenant Administrator (Organization Manager)

The Tenant Administrator represents an enterprise customer organization (e.g., `tenant-alpha` or `tenant-beta`). They manage the organization's customer registry, support teams, and SLA preferences.

#### Primary Responsibilities:
- **Agent Provisioning:** Onboarding support agents into their tenant organization in Keycloak.
- **Customer Registry:** Maintaining customer master records (email, company name, tier) in the `customers` table.
- **SLA Oversight:** Reviewing breaches of first-response and resolution deadlines.

#### Allowed Operations (What CAN Be Done):
- Query and filter all tickets belonging exclusively to their own tenant.
- Create new customer records within their tenant domain.
- Assign tickets across agents and teams in their organization.
- Soft-delete tickets when necessary for data retention compliance.

#### Forbidden Operations (What CANNOT Be Done):
- **Cross-Tenant Queries:** Cannot view, list, modify, or assign tickets belonging to any other tenant. Any attempt returns an immediate **HTTP 404 Not Found** (preventing enumeration attacks).
- **Modify Core Database Schema:** Cannot alter table definitions, enum values, or Row-Level Security policies.

---

### 3.3 Support Agent & Team Lead (Triage & Resolution Operator)

Support Agents are frontline operators who resolve inquiries submitted by customers.

#### Primary Responsibilities:
- **Triage & Claim:** Picking up unassigned `NEW` tickets and assigning them to themselves or specialist teams.
- **State Progression:** Moving tickets through the lifecycle: `OPEN` -> `IN_PROGRESS` -> `RESOLVED`.
- **Communication:** Posting public responses to customers or logging internal notes for fellow agents.

#### Allowed Operations (What CAN Be Done):
- View tickets assigned to their team or tenant queue.
- Self-assign a `NEW` ticket (which automatically advances the state to `OPEN`).
- Post `INTERNAL` comments visible only to agents and admins.
- Post `PUBLIC` comments visible to the customer.
- Transition tickets into `PENDING` (waiting for customer) or `ON_HOLD` (waiting for third-party).

#### Forbidden Operations (What CANNOT Be Done):
- **Violate State Machine Rules:** Cannot execute invalid transitions (e.g., jumping from `NEW` directly to `CLOSED` or from `RESOLVED` directly to `ON_HOLD`). The system rejects invalid transitions with **HTTP 422 Unprocessable Entity**.
- **Access Other Tenant Queues:** Cannot view or search tickets outside their tenant organization.
- **Delete Customer Records:** Cannot hard-delete customer profiles or audit histories.

---

### 3.4 End Customer / External Client (Ticket Submitter)

End Customers interact with the system by creating tickets and responding to support updates.

#### Primary Responsibilities:
- **Ticket Submission:** Submitting new support requests with subject, description, priority, and category.
- **Idempotency Protection:** Sending an `Idempotency-Key` header with write requests to guarantee network retries do not create duplicate tickets or billings.
- **Resolution Confirmation:** Confirming resolution or reopening a ticket if the issue persists within the SLA grace period.

#### Allowed Operations (What CAN Be Done):
- Submit tickets with unique `Idempotency-Key` headers.
- View public comments posted by support agents.
- Add follow-up comments to their own tickets.
- Reopen a ticket from `RESOLVED` status to `OPEN`.

#### Forbidden Operations (What CANNOT Be Done):
- **View Internal Notes:** Customers can never see comments with visibility `INTERNAL`.
- **Assign Agents:** Cannot alter ticket assignments, SLA tiers, or agent routing.
- **Access Tickets Belonging to Other Customers:** Enforced via RLS and customer ownership checks.

---

### 3.5 Autonomous AI Supervisor (Async Background Agent)

The AI Supervisor (developed in Phase 5 and Phase 6) acts as an autonomous tier-1 resolution agent.

#### Primary Responsibilities:
- **Event Consumption:** Listens to `ticket.created.v1` events published to Apache Kafka.
- **Knowledge Grounding:** Queries tenant-isolated knowledge embeddings in Qdrant (Hybrid RAG).
- **Auto-Resolution / Triage:** Categorizes incoming tickets, drafts suggested responses, and automatically resolves standard inquiries.
- **Confidence Escapes:** If the AI confidence score is below 0.85, routes the ticket directly to human agent queues.

#### Allowed Operations (What CAN Be Done):
- Consume events partitioned by `tenant_id` from Kafka.
- Suggest responses and update ticket priority or tags.
- Post public comments when confidence meets threshold.

#### Forbidden Operations (What CANNOT Be Done):
- Cannot execute destructive actions (such as closing high-priority tickets) without human confirmation.
- Cannot leak data between tenants in the vector database (enforced by tenant-isolated Qdrant collections).

---

## 4. Exhaustive Feature-by-Feature Operational Guide

### Feature 1: Cryptographic Multi-Tenancy & Zero-Trust RLS

#### How It Works:
1. Every HTTP request must supply a valid JWT or an authorized `X-Tenant-Id` header (e.g., `tenant-alpha`).
2. `TenantContextFilter` extracts the tenant identifier and stores it in thread-local `TenantContext`.
3. `TenantTransactionInterceptor` executes before any query on the active JDBC connection:
   ```sql
   SELECT set_config('app.tenant_id', 'tenant-alpha', true);
   ```
4. PostgreSQL's kernel enforces the table policy:
   ```sql
   USING (tenant_id = current_setting('app.tenant_id', true))
   ```

#### Usage Example:
```powershell
# Query tickets for tenant-alpha
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets" `
    -Method Get `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha" }
```

#### Expected Response:
```json
{
  "items": [
    {
      "id": "b1000000-0000-0000-0000-000000000001",
      "tenantId": "tenant-alpha",
      "status": "OPEN",
      "subject": "Empty package received"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### Feature 2: Core Ticketing Lifecycle Management

#### Creating a Ticket:
```powershell
$headers = @{
    "X-Tenant-Id" = "tenant-alpha"
    "Idempotency-Key" = "idem-" + [guid]::NewGuid().ToString()
    "Content-Type" = "application/json"
}
$body = @{
    customerId = "a1000000-0000-0000-0000-000000000001"
    priority = "HIGH"
    category = "ORDER"
    subject = "Defective replacement item"
    description = "Replacement item arrived damaged. Requesting immediate dispatch."
} | ConvertTo-Json

$ticket = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets" `
    -Method Post `
    -Headers $headers `
    -Body $body
```

#### Assigning a Ticket to an Agent:
Assigning an agent automatically transitions the ticket from `NEW` to `OPEN`:
```powershell
$assignPayload = @{
    agentId = "agent-alpha-007"
    team = "Tier-2 Hardware"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/$($ticket.id)/assignment" `
    -Method Put `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
    -Body $assignPayload
```

---

### Feature 3: Deterministic State Machine Enforcement

The platform enforces 8 strictly governed states:

```
  ┌───────────┐      Assign Agent
  │    NEW    │ ──────────────────────► ┌───────────┐
  └─────┬─────┘                         │   OPEN    │ ◄──────────┐
        │                               └─────┬─────┘            │
        │ Customer Follow-up                  │ Wait Vendor      │
        ▼                                     ▼                  │ Customer Reopens
  ┌───────────┐                         ┌───────────┐            │
  │  PENDING  │                         │  ON_HOLD  │            │
  └─────┬─────┘                         └─────┬─────┘            │
        │ Resume Work                         │ Resume           │
        └──────────────────► ┌────────────────┴                  │
                             ▼                                   │
                      ┌───────────┐                              │
                      │IN_PROGRESS│                              │
                      └─────┬─────┘                              │
                            │ Agent Solves                       │
                            ▼                                    │
                      ┌───────────┐                              │
                      │ RESOLVED  │ ─────────────────────────────┤
                      └─────┬─────┘                              │
                            │ Closed after SLA window            │
                            ▼                                    │
                      ┌───────────┐                              │
                      │  CLOSED   │ ─────────────────────────────┘
                      └─────┬─────┘
                            │ Mark Duplicate / Invalid
                            ▼
                      ┌───────────┐
                      │ CANCELLED │
                      └───────────┘
```

#### Valid State Transition:
```powershell
$validBody = @{
    targetStatus = "IN_PROGRESS"
    note = "Troubleshooting hardware log"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/$($ticket.id)/status" `
    -Method Patch `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
    -Body $validBody
```

#### Invalid State Transition (Rejected with HTTP 422):
Attempting an illegal transition like `NEW -> CLOSED`:
```powershell
$invalidBody = @{
    targetStatus = "CLOSED"
    note = "Trying to close directly"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/$($ticket.id)/status" `
        -Method Patch `
        -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
        -Body $invalidBody
} catch {
    Write-Host "Rejected with status: " $_.Exception.Response.StatusCode.value__
}
```
**RFC 7807 Problem Detail Returned:**
```json
{
  "type": "https://platform.enterpriseai.local/problems/invalid-state-transition",
  "title": "Invalid State Transition",
  "status": 422,
  "detail": "Cannot transition ticket from NEW to CLOSED. Permitted target states: [OPEN, CANCELLED]",
  "instance": "/api/v1/tickets/status"
}
```

---

### Feature 4: Distributed Idempotency Engine (`Idempotency-Key`)

#### How It Works:
- Clients attach an `Idempotency-Key` header with mutation requests (e.g. `Idempotency-Key: idem-12345`).
- The `IdempotencyFilter` intercepts the request:
  - If the key is new: It processes the request, commits the ticket, and saves the response payload and SHA-256 hash in `idempotency_records` using an isolated transaction.
  - If the key is replayed: It bypasses business execution entirely and returns the cached response with the header `X-Idempotent-Replay: true`.

#### Replay Verification:
```powershell
# Send duplicate request with the exact same Idempotency-Key
$replay = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/tickets" `
    -Method Post `
    -Headers $headers `
    -Body $body `
    -UseBasicParsing

Write-Host "Was Replayed:" $replay.Headers["X-Idempotent-Replay"]
Write-Host "HTTP Status:" $replay.StatusCode
```
**Result:** HTTP `201 Created` returned with `X-Idempotent-Replay: true` without creating a duplicate record in the database.

---

### Feature 5: Transactional Outbox & Event Streaming Relay

#### How It Works:
1. To avoid dual-write inconsistencies, tickets are **never** published directly to Kafka inside the web transaction.
2. Instead, an `outbox_events` row is inserted atomically alongside the ticket in PostgreSQL.
3. The `OutboxPublisherService` background poller scans pending events every 1 second:
   ```sql
   SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50;
   ```
4. It relays the canonical event envelope to Kafka with `acks=all`, using `tenant_id` as the message key.
5. Upon Kafka acknowledgment, the outbox record is marked `status = 'PUBLISHED'`.

#### Inspecting Published Events in Kafka:
```powershell
docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic ticket.created.v1 `
    --from-beginning `
    --timeout-ms 5000 `
    --max-messages 1
```

**Canonical JSON Envelope Sample:**
```json
{
  "eventId": "56797bad-0e08-4724-b961-6004ad0dad8b",
  "eventType": "ticket.created.v1",
  "eventVersion": 1,
  "tenantId": "tenant-alpha",
  "aggregateId": "474f3d81-fa47-41fa-80c3-9c41584ab4c9",
  "aggregateVersion": 1,
  "producer": "ticket-service",
  "correlationId": "idem-7d8bef13-016b-406d-b03a-d4ff23caee9a",
  "occurredAt": "2026-11-08T07:46:25.659Z",
  "payload": {
    "ticketId": "474f3d81-fa47-41fa-80c3-9c41584ab4c9",
    "status": "NEW",
    "priority": "HIGH",
    "category": "ORDER",
    "subject": "Defective replacement item"
  }
}
```

---

### Feature 6: Comments & Granular Visibility Control

#### Adding an Internal Note (Agent / Admin only):
```powershell
$internalComment = @{
    authorId = "agent-alpha-007"
    authorName = "Agent Alpha"
    authorRole = "AGENT"
    visibility = "INTERNAL"
    body = "Customer is eligible for refund under policy section 4.2."
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/$($ticket.id)/comments" `
    -Method Post `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
    -Body $internalComment
```

#### Adding a Public Customer Response:
```powershell
$publicComment = @{
    authorId = "agent-alpha-007"
    authorName = "Agent Alpha"
    authorRole = "AGENT"
    visibility = "PUBLIC"
    body = "We have reviewed your request and approved a replacement unit. Tracking #TRK-98102."
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/$($ticket.id)/comments" `
    -Method Post `
    -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } `
    -Body $publicComment
```

---

### Feature 7: SLA Tracking & Tier Calculation

When a ticket is created, `SlaTierResolver` automatically computes deadlines based on the ticket priority:

| Priority | SLA Tier | First Response Due | Resolution Due |
|---|---|---|---|
| `CRITICAL` | `ENTERPRISE` | 1 Hour | 4 Hours |
| `HIGH` | `PREMIUM` | 2 Hours | 8 Hours |
| `MEDIUM` | `STANDARD` | 4 Hours | 24 Hours |
| `LOW` | `STANDARD` | 8 Hours | 48 Hours |

SLA deadlines are permanently tracked in the `ticket_sla` table and emitted in the `TicketResponse` DTO for dashboard countdowns.

---

### Feature 8: Knowledge Management & Hybrid RAG Engine

The platform provides a local-first, zero-leakage Hybrid Retrieval-Augmented Generation (RAG) knowledge engine combining dense vector embeddings with PostgreSQL Full-Text Search.

#### 1. Ingesting a Document (Tenant Admin / Agent):
```powershell
$docPayload = @{
    title = "Hardware Replacement and Return Policy"
    content = "Customers may request refunds within 30 days of purchase for unused products in original packaging. Defective units will be replaced via RMA with prepaid return shipping labels."
    sourceType = "UPLOAD"
    metadata = @{
        department = "support"
        version = "1.0"
    }
} | ConvertTo-Json

$headers = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-alpha"
    "X-User-Id"    = "admin-001"
}

$doc = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/documents" `
    -Method Post -Headers $headers -Body $docPayload
```

#### 2. Executing a Hybrid RAG Search with Citations:
```powershell
$searchPayload = @{
    query = "replacement return window RMA"
    topK = 5
} | ConvertTo-Json

$results = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/search" `
    -Method Post -Headers $headers -Body $searchPayload
```
**Response with Formatted Citations:**
```json
{
  "query": "replacement return window RMA",
  "tenantId": "tenant-alpha",
  "totalHits": 1,
  "results": [
    {
      "chunkId": "f0345163-1e27-4a38-b48a-33b3890c51df",
      "documentId": "ec2b6f9d-6571-4bea-812c-3ff03f4e8063",
      "documentTitle": "Hardware Replacement and Return Policy",
      "version": 1,
      "chunkIndex": 0,
      "content": "Customers may request refunds within 30 days of purchase...",
      "score": 0.03278688524590164,
      "retrievalMethod": "FTS",
      "citation": "[Doc: \"Hardware Replacement and Return Policy\" v1 #chunk-0]"
    }
  ]
}
```

#### 3. Soft-Deleting a Document:
```powershell
Invoke-RestMethod -Uri "http://localhost:8082/api/v1/knowledge/documents/$($doc.id)" `
    -Method Delete -Headers $headers
```

---

### Feature 9: Human Approvals & Workflow Governance Engine

For high-risk operations (e.g. monetary refunds, order cancellations, or credit adjustments), the platform enforces strict Human-in-the-Loop (HITL) authorization via the dedicated Workflow Service (`workflow-service` on port 8083).

#### 1. Requesting Human Approval (AI Agent / System):
```powershell
$approvalPayload = @{
    ticketId = "24953765-251c-4ddd-a3ca-f48d07036a06"
    actionType = "REFUND_AUTHORIZATION"
    payload = '{"amount": 120.00, "currency": "USD", "reason": "Damaged goods in transit"}'
    requester = "ai-supervisor"
} | ConvertTo-Json

$headers = @{
    "Content-Type" = "application/json"
    "X-Tenant-Id"  = "tenant-alpha"
}

$approval = Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals" `
    -Method Post -Headers $headers -Body $approvalPayload
```

#### 2. Viewing Pending Approvals (Tenant Manager / Admin):
```powershell
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/pending" -Method Get -Headers $headers
```

#### 3. Granting Approval (Human Operator):
```powershell
$decision = @{
    approver = "manager-alpha"
    note = "Approved refund under 30-day RMA policy"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approval.id)/approve" `
    -Method Post -Headers $headers -Body $decision
```

#### 4. Rejecting an Approval Request:
```powershell
$rejection = @{
    approver = "finance-admin-alpha"
    note = "Photographic evidence of damage not provided"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/approvals/$($approval.id)/reject" `
    -Method Post -Headers $headers -Body $rejection
```

---

## 5. System Invariants: "What CAN Be Done" vs. "What CANNOT Be Done"

To guarantee operational security and data integrity, review this invariant matrix:

| Action / Capability | Allowed? | Enforcement Mechanism |
|---|---|---|
| Query tickets across different tenants in a single call | **FORBIDDEN** | PostgreSQL Row-Level Security (`app.tenant_id`) returns 404 / 0 rows |
| Bypass RLS for scheduled background event publishing | **ALLOWED** | Explicit system worker permission: `app.is_system_worker = 'true'` |
| Transition a ticket from `NEW` directly to `RESOLVED` or `CLOSED` | **FORBIDDEN** | `TicketStateMachine` validates transitions, returning HTTP 422 |
| Retry a `POST` request with identical `Idempotency-Key` | **ALLOWED** | `IdempotencyFilter` returns cached response with `X-Idempotent-Replay: true` |
| View internal comments as an end customer | **FORBIDDEN** | Query filter enforces `visibility = 'PUBLIC'` for customer requests |
| Publish directly to Kafka inside the database transaction | **FORBIDDEN** | Architectural invariant: Outbox pattern required to avoid dual-write bugs |
| Reopen a `RESOLVED` ticket back to `OPEN` | **ALLOWED** | Permitted by `TicketStateMachine` within the customer dispute window |
| Add external cloud API dependencies (OpenAI, AWS, GCP) | **FORBIDDEN** | Project charter mandate: ₹0 spend and local-first execution only |
| Exceed container memory allocations past 16 GB total | **FORBIDDEN** | Docker Compose limits enforce caps (JVM `-Xmx256m`, Postgres 512m, etc.) |

---

## 6. Troubleshooting & Maintenance Playbook

### Problem 1: Port Conflict (Port 5432, 6379, 8080, or 8081 already in use)
**Symptom:** Docker fails with `bind: address already in use`.  
**Remedy:**
```powershell
# Find and stop the local process holding the port (e.g. 5432)
Get-NetTCPConnection -LocalPort 5432 | Select-Object OwningProcess
# Or override port in .env:
# POSTGRES_PORT=5433
```

### Problem 2: Flyway Checksum Mismatch
**Symptom:** `FlywayValidateException: Migration checksum mismatch for migration version X`.  
**Remedy:**
Run Flyway repair using the tools profile:
```powershell
docker compose --profile tools run flyway-ticket-db repair
```

### Problem 3: Outbox Publisher Not Relaying Messages
**Symptom:** `outbox_events` rows remain in `PENDING` state and Kafka receives no messages.  
**Remedy:**
1. Check Kafka broker health:
   ```powershell
   docker exec enterprise-ai-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   ```
2. Inspect ticket service logs for Kafka connection errors:
   ```powershell
   docker logs enterprise-ai-ticket-service --since 2m
   ```

### Problem 4: Resetting the Environment from Scratch
**Symptom:** Data corruption during development experiments.  
**Remedy:**
```powershell
# Stop all containers and wipe volume storage
docker compose down -v
# Relaunch clean state
docker compose --profile core up -d --build
```

---

## 7. Automated Verification & Diagnostic Drills

The repository includes pre-built test scripts that run automated assertions against the running containers:

### Drill 1: Tenant Identity & Database Isolation (`Phase 2`)
```powershell
powershell -File testing/security/test-tenant-isolation.ps1
```
*Validates: JWT claims, tenant context derivation, cross-tenant RLS boundaries, and default-deny policies.*

### Drill 2: Core Ticket Isolation & State Machine (`Phase 3`)
```powershell
powershell -File testing/security/test-ticket-isolation.ps1
```
*Validates: Ticket CRUD, state machine enforcement, customer associations, and cross-tenant 404 security.*

### Drill 3: Distributed Reliability & Event Streaming (`Phase 4`)
```powershell
powershell -File testing/reliability/test-phase4-reliability.ps1
```
*Validates (11/11 PASS):*
- Spring Boot Actuator `/actuator/health` UP status.
- RLS tenant ticket list isolation.
- Cross-tenant resource denial (HTTP 404).
- Atomic outbox record creation in PostgreSQL.
- Background poller relay transition to `PUBLISHED`.
- Kafka topic receipt of canonical JSON event envelope.
- `Idempotency-Key` replay returning cached 201 with `X-Idempotent-Replay: true`.
- Invalid state transition rejection (HTTP 422 Problem JSON).
- Agent assignment auto-state change (`NEW -> OPEN`).

### Drill 4: Knowledge Management & Hybrid RAG (`Phase 5`)
```powershell
powershell -File testing/rag/test-phase5-rag.ps1
```
*Validates (12/12 PASS):*
- Knowledge Service healthcheck (`status: UP`).
- Multi-tenant document ingestion & S3 archiving with versioned keys.
- Recursive text chunking & token counting.
- Qdrant 384-dimensional dense vector indexing.
- Hybrid RAG search (PostgreSQL FTS + Qdrant vectors + Reciprocal Rank Fusion).
- Strict cross-tenant isolation (Zero leakage of Tenant Beta docs into Tenant Alpha queries).
- Precise citation generation (`[Doc: "Title" v1 #chunk-0]`).
- Soft-delete lifecycle with immediate exclusion from search results.

### Drill 5: Agentic AI & Human Approvals (`Phase 6`)
```powershell
powershell -File testing/ai/test-phase6-agent.ps1
```
*Validates (14/14 PASS):*
- Workflow Service healthcheck (`status: UP` on port 8083).
- Multi-tenant approval request generation for financial/high-risk actions.
- Pending approval querying scoped strictly by tenant.
- Strict RLS cross-tenant approval denial (HTTP 404).
- Manager approval execution (transitions to `APPROVED`).
- State machine re-approval rejection (HTTP 409 Conflict).
- Manager rejection execution (transitions to `REJECTED`).
- Full ticket audit trail preservation in PostgreSQL.

---

## Summary of Completed Phases

- **Phase 1 (Monorepo & Foundation):** Monorepo structure, Docker Compose profiles, Postgres 16 (7 DBs), Redis 7, Kafka KRaft.
- **Phase 2 (Identity & Multi-Tenancy):** Keycloak realm, JWT tenant derivation, PostgreSQL Row-Level Security, Redis key prefixing.
- **Phase 3 (Core Ticketing Microservice):** Spring Boot 3.3.4, 6-table schema, 8-state machine, RFC 7807 problem details, AOP RLS interceptor.
- **Phase 4 (Event-Driven Reliability):** Transactional outbox, 1-second poller relay, Kafka topics (`ticket.created.v1`), distributed idempotency engine.
- **Phase 5 (Knowledge Management & Hybrid RAG):** S3 object archiving, Qdrant vector search, PostgreSQL full-text search, Reciprocal Rank Fusion, citations.
- **Phase 6 (Agentic AI & Human Approvals):** LangGraph multi-agent loop, safe tool execution, and Workflow human approvals engine.

