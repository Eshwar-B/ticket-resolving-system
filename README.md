# Enterprise AI Platform

> **Local-First, Zero-Trust Multi-Tenant, Event-Driven AI Support & Workflow Automation Platform**  
> **Development Standard:** BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT  
> **Cost Model:** Strictly **₹0** (100% open-source local stack — no paid cloud APIs)  
> **Resource Budget:** Optimized for **16 GB RAM** (Windows 11 / Linux / macOS)  

[![CI Pipeline](https://github.com/Eshwar-B/ticket-resolving-system/actions/workflows/ci.yml/badge.svg)](https://github.com/Eshwar-B/ticket-resolving-system/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java: 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Spring Boot: 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io)
[![PostgreSQL: 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org)
[![Kafka: KRaft](https://img.shields.io/badge/Kafka-3.8.0%20(KRaft)-black.svg)](https://kafka.apache.org)

---

## Table of Contents

1. [Architectural Blueprint](#1-architectural-blueprint)
2. [Current Delivery Status (Phase 1 – Phase 4)](#2-current-delivery-status-phase-1--phase-4)
3. [Zero-Trust Multi-Tenancy Architecture](#3-zero-trust-multi-tenancy-architecture)
4. [Event-Driven Reliability & Transactional Outbox](#4-event-driven-reliability--transactional-outbox)
5. [Core Ticket Domain & State Machine](#5-core-ticket-domain--state-machine)
6. [Hardware Discipline & Resource Profile (16 GB RAM Cap)](#6-hardware-discipline--resource-profile-16-gb-ram-cap)
7. [Getting Started & Local Quickstart](#7-getting-started--local-quickstart)
8. [Automated Test Suites & Verification Drills](#8-automated-test-suites--verification-drills)
9. [API Reference & Sample Invocations](#9-api-reference--sample-invocations)
10. [Repository Structure](#10-repository-structure)
11. [Governance & Reverse-Engineering Guides](#11-governance--reverse-engineering-guides)

---

## 1. Architectural Blueprint

The **Enterprise AI Platform** is an enterprise-scale autonomous customer service and ticketing system designed with a strict physical separation of concerns:

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

- **Synchronous Control Plane (Java 21 / Spring Boot 3.3.4):** High-throughput, deterministic transaction management, strict state transitions, and PostgreSQL Row-Level Security.
- **Asynchronous AI Plane (Python 3.12 / LangGraph / Ollama):** Non-blocking event consumers, autonomous triage agents, knowledge grounding with Qdrant, and local vector embeddings.
- **Zero-Cloud-Cost Infrastructure (Docker Compose):** KRaft Kafka (no ZooKeeper), PostgreSQL 16 with per-service databases, and Redis 7.

---

## 2. Current Delivery Status (Phase 1 – Phase 4)

| Phase | Milestone | Technology | Status | Key Deliverables & Evidence |
|---|---|---|---|---|
| **Phase 1** | **Monorepo & Foundation** | Docker, Postgres, Redis, Kafka | **VERIFIED** | 7 isolated service DBs, KRaft single-node, health verified |
| **Phase 2** | **Identity & Multi-Tenancy** | Keycloak OIDC, PostgreSQL RLS | **VERIFIED** | JWT tenant derivation, `test-tenant-isolation.ps1` (5/5 PASS) |
| **Phase 3** | **Core Ticket Service** | Spring Boot 3.3.4, Java 21, Flyway | **VERIFIED** | 6-table schema, 8-state machine, `test-ticket-isolation.ps1` (10/10 PASS) |
| **Phase 4** | **Event-Driven Reliability** | Outbox, Idempotency, Kafka Topics | **VERIFIED** | Dual-write prevention, `test-phase4-reliability.ps1` (11/11 PASS) |
| **Phase 5** | **Knowledge & Hybrid RAG** | MinIO, Qdrant, FastEmbed | *Next Active* | Tenant document ingestion, hybrid chunking, BM25 + dense |
| **Phase 6** | **LangGraph AI Resolution** | LangGraph, Ollama, PII Presidio | *Upcoming* | Autonomous agent loop, guardrails, confidence gates |
| **Phase 7** | **Observability & Audit** | OpenTelemetry, Phoenix, Prometheus | *Upcoming* | Tracing context propagation, tenant-scoped audit logging |

---

## 3. Zero-Trust Multi-Tenancy Architecture

Multi-tenancy in the Enterprise AI Platform is **cryptographically derived and enforced at the database kernel level**.

```
[ Incoming HTTP Request ]
       │
       ▼
TenantContextFilter (Order 1)
       │  Extracts JWT claim `tenant_id` (fallback to `X-Tenant-Id` for service mesh)
       ▼
TenantTransactionInterceptor (AOP @Around @Transactional)
       │  Executes before ANY query on the JDBC Connection:
       │  SELECT set_config('app.tenant_id', ?, true);   <-- Transaction-scoped
       ▼
PostgreSQL Engine Kernel
       │  Evaluates Row-Level Security:
       │  CREATE POLICY ON tickets USING (tenant_id = current_setting('app.tenant_id', true));
       ▼
[ Guaranteed Zero Cross-Tenant Data Leakage ]
```

### PostgreSQL Row-Level Security (RLS) Guarantees
- **No Shared Tenant Data:** Every single query executed against `tickets`, `customers`, `comments`, `history`, `sla`, `outbox_events`, and `idempotency_records` is filtered by PostgreSQL RLS.
- **Default Deny:** If `app.tenant_id` is empty or unset, PostgreSQL returns **0 rows**. Unauthenticated or cross-tenant lookups fail safely as HTTP 404 (preventing ID enumeration).
- **Tenant-Scoped Redis:** All cache and rate-limit keys use strict prefixing: `t:{tenant_id}:{subsystem}:{key}`.
- **Tenant-Partitioned Kafka:** All messages in Kafka topics use `tenant_id` as the message key to preserve per-tenant FIFO ordering.

---

## 4. Event-Driven Reliability & Transactional Outbox

To prevent the distributed dual-write problem (writing to DB while publishing to Kafka), Phase 4 introduces a zero-data-loss architecture:

### 1. Atomic Transactional Outbox
1. When a ticket is created, updated, or assigned, the entity mutation and an `outbox_events` row are inserted in the **exact same ACID database transaction**.
2. A resilient background scheduler (`OutboxPublisherService`) queries pending outbox events across all tenants using system-worker credentials (`set_config('app.is_system_worker', 'true', true)`).
3. Events are published to Kafka with delivery acknowledgments (`acks=all`), transitioning event status to `PUBLISHED`.

### 2. Distributed Idempotency (`Idempotency-Key`)
- All mutation endpoints accept an `Idempotency-Key` header.
- `IdempotencyFilter` checks `idempotency_records` using isolated transactions (`Propagation.REQUIRES_NEW`).
- **Exact Replay Guarantee:** Retried requests with the identical key return the cached HTTP status, headers, and body with header `X-Idempotent-Replay: true` without re-executing business logic.
- Concurrent conflicts trigger RFC 7807 **HTTP 409 Conflict** (`idempotency-conflict`).

---

## 5. Core Ticket Domain & State Machine

The ticketing core enforces a deterministic 8-state lifecycle with 15 valid transitions:

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

- **Deterministic Validation:** Illegal transitions (e.g. attempting to jump `NEW -> CLOSED`) are rejected with **HTTP 422 Unprocessable Entity** (`invalid-state-transition`).
- **Auto-Assignment Transitions:** Assigning an unassigned `NEW` ticket automatically advances its state to `OPEN`.

---

## 6. Hardware Discipline & Resource Profile (16 GB RAM Cap)

The platform is purpose-built to run sustainably on everyday developer hardware (16 GB RAM, 4 GB VRAM) with **₹0 cloud dependencies**:

| Container | Base Image | Memory Limit | JVM / Process Heap | Role |
|---|---|---|---|---|
| `enterprise-ai-postgres` | `postgres:16-alpine` | 512 MiB | shared_buffers=128MB | 7 Logical DBs + RLS |
| `enterprise-ai-redis` | `redis:7-alpine` | 256 MiB | maxmemory 128MB | Cache & Rate Limiting |
| `enterprise-ai-kafka` | `apache/kafka:3.8.0` | 1024 MiB | KRaft Broker + Controller | Event Streaming |
| `enterprise-ai-keycloak` | `keycloak:24.0.5` | 768 MiB | `-Xmx384m` | OIDC Identity Provider |
| `enterprise-ai-ticket-service` | `eclipse-temurin:21` | 384 MiB | `-Xmx256m` (G1GC) | Ticketing Control Plane |
| **Total Measured Footprint** | | **~1250 MiB** | **~8% of 16 GB** | Safe headroom for Ollama |

---

## 7. Getting Started & Local Quickstart

### Prerequisites
- **OS:** Windows 11 / Ubuntu 22.04+ / macOS 13+
- **Docker:** Docker Desktop or Docker Engine 24+
- **Java:** JDK 21+ (Eclipse Temurin recommended)
- **Shell:** PowerShell 5.1+ (Windows) or Bash

### 1. Clone & Configure
```bash
git clone https://github.com/Eshwar-B/ticket-resolving-system.git
cd ticket-resolving-system
cp .env.example .env
```

### 2. Launch Core Services
Start PostgreSQL, Redis, Kafka KRaft, Keycloak, and the Ticket Microservice:
```powershell
docker compose --profile core up -d
```

### 3. Verify System Health
Check container liveness and actuator endpoints:
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/actuator/health"
# Returns: @{status=UP; ...}
```

---

## 8. Automated Test Suites & Verification Drills

Three automated test suites guarantee non-regression, tenant isolation, and event reliability:

### 1. Multi-Tenant Identity & PostgreSQL RLS (`Phase 2`)
```powershell
powershell -File testing/security/test-tenant-isolation.ps1
```
*Assertions:* JWT signature validation, tenant context derivation, cross-tenant isolation, default-deny policy.

### 2. Core Ticketing Service & State Machine (`Phase 3`)
```powershell
powershell -File testing/security/test-ticket-isolation.ps1
```
*Assertions:* Cross-tenant queries return 404, valid state transitions persist, invalid transitions return 422 RFC 7807 Problem JSON.

### 3. Distributed Reliability & Outbox Pipeline (`Phase 4`)
```powershell
powershell -File testing/reliability/test-phase4-reliability.ps1
```
*Assertions (11/11 PASS):*
- Spring Boot Actuator liveness
- Scoped tenant pagination (`tenant-alpha` vs `tenant-beta`)
- Cross-tenant resource denial (HTTP 404)
- Atomic Outbox recording in PostgreSQL
- Polling relay publishing to Apache Kafka
- Kafka message consumption & canonical JSON envelope validation
- `Idempotency-Key` replay returning cached response with `X-Idempotent-Replay: true`
- State machine violation rejection (HTTP 422)
- Agent assignment auto-state change (`NEW -> OPEN`)

---

## 9. API Reference & Sample Invocations

Base URL: `http://localhost:8081`

### Create a Ticket (with Distributed Idempotency)
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
    subject = "Damaged shipment delivered"
    description = "Package #44109 was crushed on arrival."
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets" -Method Post -Headers $headers -Body $body
```

### List Tenant Tickets (RLS-Scoped)
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets?page=0&size=10" -Headers @{ "X-Tenant-Id" = "tenant-alpha" }
```

### Assign Ticket to Agent
```powershell
$assignBody = @{
    agentId = "agent-alpha-007"
    team = "Tier-2 Support"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/<ticket-id>/assignment" -Method Put -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } -Body $assignBody
```

### Transition State
```powershell
$transBody = @{
    targetStatus = "IN_PROGRESS"
    note = "Investigating with carrier"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/tickets/<ticket-id>/status" -Method Patch -Headers @{ "X-Tenant-Id" = "tenant-alpha"; "Content-Type" = "application/json" } -Body $transBody
```

---

## 10. Repository Structure

```
ticket-resolving-system/
├── backend/                               # Java 21 / Spring Boot 3 Control Plane
│   ├── common/                            # Shared TenantContext, RLS enforcer, Event Envelopes
│   ├── gateway/                           # Spring Cloud Gateway & Redis Rate Limiter
│   ├── identity-tenant-service/           # Keycloak user sync & tenant metadata
│   ├── ticket-service/                    # Core Ticketing & Reliability Engine
│   │   ├── src/main/java/com/enterpriseai/ticket/
│   │   │   ├── config/                    # TenantContextFilter, IdempotencyFilter, AOP RLS
│   │   │   ├── domain/                    # TicketStateMachine, Enums, Status rules
│   │   │   ├── dto/                       # Requests, Responses, RFC 7807 ProblemDetail
│   │   │   ├── entity/                    # JPA Entities (Ticket, OutboxEvent, IdempotencyRecord)
│   │   │   ├── repository/                # Spring Data JPA Repositories
│   │   │   ├── service/                   # TicketService, OutboxPublisher, IdempotencyService
│   │   │   └── web/                       # REST Controllers & GlobalExceptionHandler
│   │   └── src/main/resources/db/migration/ # Flyway V1, V2, V3 migrations
│   └── pom.xml                            # Root Maven multi-module parent
├── contracts/                             # Shared JSON Event Schemas
│   └── events/                            # envelope.v1.json, ticket.created.v1.json
├── infra/                                 # Infrastructure configurations
│   ├── docker/postgres/                   # Database provisioning scripts
│   ├── keycloak/                          # Pre-configured realm export
│   └── observability/                     # OpenTelemetry collector configuration
├── testing/                               # Automated verification suites
│   ├── security/                          # Phase 2 & Phase 3 isolation tests
│   └── reliability/                       # Phase 4 Outbox & Idempotency tests
├── docs/                                  # Complete system documentation
│   ├── agent/                             # Living execution logs & project state
│   └── reverse-engineering/               # Detailed technical reverse-engineering guides
├── docker-compose.yml                     # Multi-profile container orchestration
├── ENTERPRISE_AI_PLATFORM_BLUEPRINT.md    # Master architectural specification
└── README.md                              # This document
```

---

## 11. Governance & Reverse-Engineering Guides

Every completed capability is paired with a technical reverse-engineering guide documenting implementation rationales, PostgreSQL engine behaviors, and performance characteristics:

- [Phase 1: Monorepo & Zero-Cost Infrastructure Blueprint](docs/reverse-engineering/phase-1/01-monorepo-and-infrastructure.md)
- [Phase 1: OpenTelemetry Collector & JSON Logging](docs/reverse-engineering/phase-1/02-observability-foundation.md)
- [Phase 2: PostgreSQL Row-Level Security & Keycloak Isolation](docs/reverse-engineering/phase-2/01-multi-tenancy-and-rls.md)
- [Phase 3: Core Ticket Domain & Deterministic State Machine](docs/reverse-engineering/phase-3/01-ticket-service-core.md)
- [Phase 4: Transactional Outbox, Distributed Idempotency & Kafka Messaging](docs/reverse-engineering/phase-4/01-transactional-outbox-and-idempotency.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
