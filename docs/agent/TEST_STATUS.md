# Platform Test Status — Phase 4 (docs/test-status.md)

---

## Phase 4 Event-Driven Reliability & Outbox Test Suite (All 11 Tests Executed Live)

Executed via: `powershell -ExecutionPolicy Bypass -File .\testing\reliability\test-phase4-reliability.ps1`

| Test ID | Test Name | Description | Status | Evidence |
|---|---|---|---|---|
| **REL-P4-01** | `Actuator_Health_UP` | Spring Boot Actuator liveness probe | **PASS** | `{"status":"UP"}` |
| **REL-P4-02** | `HTTP_Alpha_Ticket_List` | Alpha receives only Alpha-scoped tickets | **PASS** | 6 tickets returned, all `tenant-alpha` |
| **REL-P4-03** | `HTTP_Beta_Ticket_List` | Beta receives only Beta-scoped tickets | **PASS** | 1 ticket returned, all `tenant-beta` |
| **REL-P4-04** | `HTTP_Cross_Tenant_404` | Beta querying Alpha ticket returns 404 | **PASS** | HTTP 404 Resource Not Found |
| **REL-P4-05** | `HTTP_Create_Ticket` | Mutation creates ticket with 201 Created | **PASS** | Valid UUID returned with status `NEW` |
| **REL-P4-06** | `Idempotency_Key_Replay` | Replay with same key returns cached response | **PASS** | Header `X-Idempotent-Replay: true` |
| **REL-P4-07** | `Outbox_Event_Recorded` | Atomic outbox row inserted in `ticket_db` | **PASS** | Row present in `outbox_events` |
| **REL-P4-08** | `Outbox_Published_Status` | Background poller relays to Kafka | **PASS** | Status transitioned to `PUBLISHED` |
| **REL-P4-09** | `Kafka_Message_Received` | Kafka topic receives canonical JSON envelope | **PASS** | `ticket.created.v1` consumed with matching ID |
| **REL-P4-10** | `State_Machine_Rejection` | Invalid transition `NEW -> CLOSED` rejected | **PASS** | HTTP 422 Problem JSON (`invalid-state-transition`) |
| **REL-P4-11** | `Ticket_Assignment_Auto_Open` | Agent assignment auto-transitions `NEW -> OPEN` | **PASS** | Status updated to `OPEN` |

---

## Phase 3 Ticket Service Isolation Test Suite (Carried Forward)

Executed via: `powershell -ExecutionPolicy Bypass -File .\testing\security\test-ticket-isolation.ps1`

| Test ID | Test Name | Description | Status | Evidence |
|---|---|---|---|---|
| **SEC-P3-01** | `RLS_Alpha_Ticket_Count` | tenant-alpha sees only its own tickets | **PASS** | 1 ticket returned (Alpha seed ticket) |
| **SEC-P3-02** | `RLS_Beta_Ticket_Count` | tenant-beta sees only its own tickets | **PASS** | 1 ticket returned (Beta seed ticket) |
| **SEC-P3-03** | `RLS_Cross_Tenant_Denial` | Alpha querying Beta ticket UUID returns zero rows | **PASS** | 0 rows returned |
| **SEC-P3-04** | `RLS_Default_Deny_Tickets` | Querying tickets without `app.tenant_id` context returns zero rows | **PASS** | 0 rows returned |
| **SEC-P3-05** | `RLS_Customer_Alpha_Isolation` | tenant-alpha sees only its customer records | **PASS** | 2 customers returned; 0 Beta customers |
| **SEC-P3-06** | `Seed_Data_Integrity` | Seed ticket idempotency key present and searchable | **PASS** | `seed-ticket-alpha-001` verified |
| **SEC-P3-07** | `Schema_Status_Enum` | PostgreSQL `ticket_status` enum contains all 8 valid lifecycle states | **PASS** | `NEW, OPEN, IN_PROGRESS, WAITING_FOR_CUSTOMER, WAITING_FOR_APPROVAL, RESOLVED, CLOSED, CANCELLED` |
| **SEC-P3-08** | `Schema_RLS_All_Tables` | RLS is enabled and forced on all 6 ticket tables | **PASS** | 6/6 tables (`customers`, `tickets`, `ticket_comments`, `ticket_assignments`, `ticket_history`, `ticket_sla`) |
| **SEC-P3-09** | `Schema_Ticket_Indexes` | Trigram, composite, and partial indexes created | **PASS** | 10 indexes verified on `tickets` |
| **SEC-P3-10** | `Permissions_App_User_Select` | Non-superuser `app_user` can select within RLS bounds | **PASS** | Successful query execution |

---

## Phase 2 Security & Isolation Test Suite (Carried Forward)

| Test ID | Description | Assertion | Status | Evidence |
|---|---|---|---|---|
| **SEC-P2-01** | RLS Tenant Alpha Isolation | `app_user` with `tenant-alpha` context sees only Alpha rows | **PASS** | `tenant-alpha \| TENANT_ADMIN` returned; zero Beta rows |
| **SEC-P2-02** | RLS Tenant Beta Isolation | `app_user` with `tenant-beta` context sees only Beta rows | **PASS** | `tenant-beta \| TENANT_ADMIN` returned; zero Alpha rows |
| **SEC-P2-03** | Default Deny (No Context) | Zero rows returned when `app.tenant_id` is not set | **PASS** | `COUNT(*) = 0` without `SET LOCAL` |
| **SEC-P2-04** | Connection Pool Leakage | `SET LOCAL` resets on `COMMIT`; next session has empty `app.tenant_id` | **PASS** | `current_setting('app.tenant_id', true)` → `''` after commit |
| **SEC-P2-05** | Redis Key Namespace Isolation | Tenant Alpha and Beta keys do not collide; each reads own value | **PASS** | `t:tenant-alpha:user:profile` → `alpha-profile-data` exclusively |
| **SEC-P2-06** | Keycloak JWT Tenant Claim | JWT for `alice@alpha.com` contains `tenant_id: "tenant-alpha"` | **PASS** | Decoded `tenant_id` = `"tenant-alpha"` from live token |

---

## Phase 1 Verification Suite (Carried Forward)

| Test | Status | Notes |
|---|---|---|
| Docker Compose Config Validation | **PASS** | Passes with Keycloak & Ticket Service |
| PostgreSQL Health | **PASS** | Healthy |
| PostgreSQL Databases (7+) | **PASS** | All service DBs + keycloak_db |
| Redis Connectivity | **PASS** | Healthy |
| Kafka KRaft Produce/Consume | **PASS** | Healthy |
| Flyway identity_db Migration | **PASS** | V1 applied — tenants, users, memberships, RLS |
| Flyway ticket_db Migration | **PASS** | V1 baseline + V2 full schema applied |

---

## Live Resource Usage (Core Stack)

| Container | Memory | Limit | % |
|---|---|---|---|
| `enterprise-ai-keycloak` | 519.6 MiB | 768 MiB | 67.65% |
| `enterprise-ai-postgres` | 74.0 MiB | 512 MiB | 14.42% |
| `enterprise-ai-redis` | 8.3 MiB | 256 MiB | 3.25% |
| `enterprise-ai-kafka` | 382.5 MiB | 1024 MiB | 37.36% |
| **Total Stack** | **~984 MiB** | — | **~6% of 16 GB host** |
