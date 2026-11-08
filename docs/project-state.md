# Platform Project State (docs/project-state.md)

**Current Phase:** Phase 3 — COMPLETE  
**Next Phase:** Phase 4 — Event-driven Reliability (Outbox, Idempotency, DLQ)  
**Last Updated:** 2026-11-07  
**Operating Philosophy:** BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT  
**Cost Model:** ₹0 (Local-First Open-Source Stack)  
**Hardware Profile:** Windows 11, 16 GB RAM, AMD Ryzen 5 5600H, RTX 3050 Laptop GPU (4 GB VRAM)

---

## Measured Memory Usage (Phase 3 — Core + Keycloak + Ticket Service schema)

| Container | CPU % | Memory | Limit | % |
|---|---|---|---|---|
| `enterprise-ai-keycloak` | 0.11% | 519.6 MiB | 768 MiB | 67.65% |
| `enterprise-ai-postgres` | 0.08% | 74 MiB | 512 MiB | 14.42% |
| `enterprise-ai-redis` | 3.26% | 8.3 MiB | 256 MiB | 3.25% |
| `enterprise-ai-kafka` | 60.57% | 382.5 MiB | 1024 MiB | 37.36% |
| `ticket-service` (built) | — | ~256 MiB (Xmx cap) | 384 MiB | — |
| **Total (projected)** | | **~1240 MiB** | | **~8% of 16 GB** |

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
| **PostgreSQL RLS** | Row-Level Security | Phase 2 | **VERIFIED** | Isolation + default-deny + pool-leakage drills PASSED |
| **TenantContext** | Java ThreadLocal | Phase 2 | **READY** | `TenantContext.java` authored in `backend/common` |
| **RLS Enforcer** | Java Postgres interceptor | Phase 2 | **READY** | `PostgresTenantRlsEnforcer.java` authored |
| **Redis Key Naming** | Java utility | Phase 2 | **VERIFIED** | `TenantRedisKey.java` + live cross-tenant isolation test |
| **ticket_db Schema** | PostgreSQL + Flyway V2 | Phase 3 | **VERIFIED** | 6 tables + RLS + enums + indexes applied |
| **Ticket State Machine** | Java enum + TicketStateMachine | Phase 3 | **READY** | All 8 states + 15 transitions coded |
| **Ticket Service** | Java 21 / Spring Boot 3 | Phase 3 | **READY** | Full Spring Boot app scaffolded |
| **Ticket REST API** | Spring MVC + OpenAPI | Phase 3 | **READY** | 8 endpoints + RFC 7807 error handler |
| **Ticket RLS Interceptor** | Spring AOP + SET LOCAL | Phase 3 | **READY** | TenantTransactionInterceptor coded |
| **Ticket Isolation Tests** | PowerShell test suite | Phase 3 | **VERIFIED** | 10/10 categories PASS |
| **Outbox Pattern** | Postgres + Kafka publisher | Phase 4 | *Next Phase* | Planned for Phase 4 |
| **RAG / Knowledge** | Python + Qdrant + MinIO | Phase 5 | *Deferred* | Planned for Phase 5 |
| **AI Agents** | Python LangGraph | Phase 6 | *Deferred* | Planned for Phase 6 |
