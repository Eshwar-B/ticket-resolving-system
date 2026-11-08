# Enterprise AI Platform

> **Local-First, Multi-Tenant, Event-Driven AI Support, Knowledge & Workflow Automation Platform**  
> **Development Philosophy:** BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT  
> **Cost Constraint:** Strictly ₹0 (Open-source, local-first stack)  
> **Hardware Budget:** Optimized for Windows 11 / 16 GB RAM  

---

## 1. Project Overview

The **Enterprise AI Platform** is an enterprise-grade customer support, knowledge retrieval (RAG), and agentic workflow automation system. The architecture cleanly separates:

- **Java Control Plane (Spring Boot 3 / Java 21):** Synchronous request ingress, Gateway, Identity & Tenant isolation, Ticket state machine, Transactional Outbox, and SLA management.
- **Python AI Plane (FastAPI / Python 3.12 / LangGraph):** Asynchronous event consumption, Agentic supervisors, Tool calling, Hybrid RAG with Qdrant, Guardrails, and Model evaluation.
- **Core Infrastructure (Docker Compose):** PostgreSQL 16 (per-service logical databases), Redis 7 (caching, rate-limiting), and Apache Kafka (KRaft mode, no ZooKeeper).
- **Observability Stack:** OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo, and Phoenix.

---

## 2. Current Status: Phase 1 (Foundation)

| Milestone | Status | Description |
|---|---|---|
| **Monorepo Layout** | **COMPLETED** | Standardized monorepo skeleton across `backend/`, `ai-plane/`, `contracts/`, `infra/`, `docs/` |
| **Docker Compose** | **COMPLETED** | Multi-profile specification (`core`, `ai`, `obs`, `tools`, `full`) with memory caps |
| **PostgreSQL 16** | **COMPLETED** | Initialization hook provisioning 7 per-service databases and least-privilege roles |
| **Redis 7** | **COMPLETED** | Configured with persistence, health checks, and 128 MB volatile-lru policy |
| **Kafka (KRaft)** | **COMPLETED** | Single-node Kafka broker + controller without ZooKeeper dependency |
| **Flyway Migrations** | **COMPLETED** | Standalone runner & version-controlled baseline migration infrastructure |
| **CI Pipeline** | **COMPLETED** | GitHub Actions workflow validating compose configs, syntax, and secrets |
| **Observability** | **COMPLETED** | OpenTelemetry Collector configuration and structured JSON logging templates |

---

## 3. Quickstart: Running Phase 1 Infrastructure

### Prerequisites
- Windows 11 (or Linux/macOS)
- Docker Desktop (running)
- PowerShell (Windows) or Make (Linux/macOS)

### 1. Environment Configuration
Copy the provided `.env.example` to `.env`:
```powershell
cp .env.example .env
```

### 2. Start Core Infrastructure (Postgres, Redis, Kafka KRaft)
Using PowerShell:
```powershell
.\scripts\dev.ps1 up-core
```
Or using Make:
```bash
make up-core
```

### 3. Verify Health & Connectivity
Run the automated verification suite to test PostgreSQL per-service databases, Redis set/get, Kafka message production & consumption, and Flyway migrations:
```powershell
.\scripts\dev.ps1 test-infra
```

### 4. Check Status & Resource Usage
Ensure memory consumption remains well within the 16 GB laptop limits:
```powershell
.\scripts\dev.ps1 status
```

### 5. Tear Down Infrastructure
```powershell
.\scripts\dev.ps1 down
```

---

## 4. Key Architectural Documents

- [`ENTERPRISE_AI_PLATFORM_BLUEPRINT.md`](./ENTERPRISE_AI_PLATFORM_BLUEPRINT.md) — Master Architecture & Blueprint
- [`AGENT.md`](./AGENT.md) — Autonomous Operating Standards & Rules
- [`guidlines.md`](./guidlines.md) — Project Vision & Engineering Standards
- [`docs/reverse-engineering/phase-1/`](./docs/reverse-engineering/phase-1/) — Phase 1 Reverse-Engineering In-Depth Guides
- [`docs/agent/`](./docs/agent/) — Living project state, tasks, and blocker tracking

"# ticket-resolving-system" 
