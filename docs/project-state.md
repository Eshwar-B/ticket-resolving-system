# Platform Project State (docs/project-state.md)

**Current Phase:** Phase 4 — COMPLETE (Phase 3 & Phase 4 fully verified)  
**Next Phase:** Phase 5 — Knowledge Service & RAG Ingestion Pipeline  
**Last Updated:** 2026-11-08  
**Operating Philosophy:** BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT  
**Cost Model:** ₹0 (Local-First Open-Source Stack)  
**Hardware Profile:** Windows 11, 16 GB RAM, AMD Ryzen 5 5600H, RTX 3050 Laptop GPU (4 GB VRAM)

---

## Measured Memory Usage (Phase 3 & 4 — Core + Keycloak + Ticket Service + Kafka KRaft)

| Container | CPU % | Memory | Limit | % |
|---|---|---|---|---|
| `enterprise-ai-keycloak` | 0.11% | 519.6 MiB | 768 MiB | 67.65% |
| `enterprise-ai-postgres` | 0.08% | 85 MiB | 512 MiB | 16.60% |
| `enterprise-ai-redis` | 3.26% | 8.3 MiB | 256 MiB | 3.25% |
| `enterprise-ai-kafka` | 15.20% | 410.5 MiB | 1024 MiB | 40.08% |
| `enterprise-ai-ticket-service` | 0.25% | 230 MiB | 384 MiB | 59.89% |
| **Total Measured** | | **~1253 MiB** | | **~8% of 16 GB** |

---

## Component Readiness Matrix

| Component | Technology | Phase | Status | Verification Evidence |
|---|---|---|---|---|
| **Monorepo Structure** | Multi-package layout | Phase 1 | **VERIFIED** | Phase 1 complete |
| **PostgreSQL 16** | Docker (`postgres:16-alpine`) | Phase 1 | **VERIFIED** | 7+ service DBs, app_user role, healthy |
| **Redis 7** | Docker (`redis:7-alpine`) | Phase 1 | **VERIFIED** | PING + tenant KV verified |
| **Kafka (KRaft)** | Docker (`apache/kafka:3.8.0`) | Phase 1 | **VERIFIED** | Produce/consume verified |
| **Flyway Migrations** | Docker (`flyway/flyway:10-alpine`) | Phase 1 | **VERIFIED** | ticket_db + identity_db migrated |
| **Keycloak OIDC** | Docker (`keycloak:24.0.5`) | Phase 2 | **VERIFIED** | Realm imported, JWT with tenant_id issued |
| **identity_db Schema** | PostgreSQL + Flyway | Phase 2 | **VERIFIED** | tenants, users, memberships, RLS policies created |
| **PostgreSQL RLS** | Row-Level Security | Phase 2 | **VERIFIED** | Isolation + default-deny + pool-leakage drills PASSED (5/5) |
| **TenantContext** | Java ThreadLocal | Phase 2 | **VERIFIED** | `TenantContext.java` in `backend/common` |
| **RLS Enforcer** | Java Postgres interceptor | Phase 2 | **VERIFIED** | `PostgresTenantRlsEnforcer.java` using `set_config` |
| **Redis Key Naming** | Java utility | Phase 2 | **VERIFIED** | `TenantRedisKey.java` + live cross-tenant isolation test |
| **ticket_db Schema** | PostgreSQL + Flyway V2/V3 | Phase 3 | **VERIFIED** | 9 tables + RLS + enums + indexes applied |
| **Ticket State Machine** | Java enum + TicketStateMachine | Phase 3 | **VERIFIED** | 8 states + 15 transitions, invalid rejected (HTTP 422) |
| **Ticket Service** | Java 21 / Spring Boot 3 | Phase 3 | **VERIFIED** | Full Spring Boot app healthy in container |
| **Ticket REST API** | Spring MVC + OpenAPI | Phase 3 | **VERIFIED** | CRUD, assignment, status transition, RFC 7807 problem+json |
| **Ticket RLS Interceptor** | Spring AOP + set_config | Phase 3 | **VERIFIED** | TenantTransactionInterceptor verified |
| **Ticket Isolation Tests** | PowerShell test suite | Phase 3 | **VERIFIED** | 10/10 categories PASS (`test-ticket-isolation.ps1`) |
| **Outbox Pattern** | Postgres + Kafka publisher | Phase 4 | **VERIFIED** | Atomic outbox + 1s poller relay to Kafka (`ticket.created.v1`) |
| **Idempotency Engine** | Filter + IdempotencyService | Phase 4 | **VERIFIED** | `Idempotency-Key` replay returns cached 201 with header |
| **Reliability Test Suite** | PowerShell end-to-end | Phase 4 | **VERIFIED** | 11/11 tests PASS (`test-phase4-reliability.ps1`) |
| **RAG / Knowledge** | Python + Qdrant + MinIO | Phase 5 | *Next Phase* | Planned for Phase 5 |
| **AI Agents** | Python LangGraph | Phase 6 | *Deferred* | Planned for Phase 6 |
