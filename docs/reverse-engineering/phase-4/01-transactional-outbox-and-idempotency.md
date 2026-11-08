# Reverse Engineering: Transactional Outbox, Distributed Idempotency & Canonical Events

**Phase:** Phase 4 — Event-Driven Reliability & Distributed Systems  
**Service:** `ticket-service` (Java 21 / Spring Boot 3.3.4 / Apache Kafka KRaft / PostgreSQL 16)  
**Status:** COMPLETE & VERIFIED (11/11 automated tests passed)  
**Date:** 2026-11-08  

---

## 1. Architectural Overview & The Dual-Write Problem

In a distributed microservice architecture, state mutations (e.g., creating a ticket in PostgreSQL) and event publications (e.g., publishing `ticket.created.v1` to Apache Kafka) must never diverge. The classical anti-pattern is:

```java
// ANTI-PATTERN: Prone to silent data corruption
ticketRepository.save(ticket);
kafkaTemplate.send("ticket.created.v1", event); // If Kafka fails, DB is committed but event is lost!
```

To eliminate distributed inconsistency with **₹0 cloud spend and strict hardware constraints (16 GB RAM)**, Phase 4 implements three reliability pillars:
1. **Transactional Outbox Pattern:** Atomic DB commits of entity mutations and outbox records in a single PostgreSQL transaction, asynchronously relayed to Kafka by a scheduled poller.
2. **Distributed Idempotency Engine:** `Idempotency-Key` HTTP header caching and request-hash verification with automatic replay and RFC 7807 409 conflict detection.
3. **Canonical Event Envelope:** Strict JSON schema contracts (`contracts/events/envelope.v1.json`) guaranteeing backward compatibility, tenant-scoped partitioning, and correlation tracking.

---

## 2. Transactional Outbox Implementation

### 2.1 Outbox Schema (`V3__outbox_and_reliability.sql`)

```sql
CREATE TABLE outbox_events (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         TEXT        NOT NULL,
    topic             TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    event_version     INTEGER     NOT NULL DEFAULT 1,
    aggregate_id      TEXT        NOT NULL,
    aggregate_version INTEGER     NOT NULL DEFAULT 1,
    correlation_id    TEXT,
    idempotency_key   TEXT,
    payload           JSONB       NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'PENDING',
    retry_count       INTEGER     NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ,
    error_message     TEXT
);

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;

CREATE POLICY outbox_events_tenant_isolation ON outbox_events
    USING (
        tenant_id = current_setting('app.tenant_id', true)
        OR current_setting('app.is_system_worker', true) = 'true'
    )
    WITH CHECK (
        tenant_id = current_setting('app.tenant_id', true)
        OR current_setting('app.is_system_worker', true) = 'true'
    );
```

### 2.2 System Background Worker Bypass for Multi-Tenancy

Because `outbox_events` is governed by PostgreSQL Row-Level Security (RLS), the background poller `OutboxPublisherService` (which runs across all tenants) must be able to select pending events without violating tenant isolation for regular user web requests.

This is solved cleanly via:
```sql
SELECT set_config('app.is_system_worker', 'true', true);
```
Executed within the background transaction. Normal HTTP requests never set `app.is_system_worker`, guaranteeing zero tenant data leakage.

### 2.3 Atomic Recording & Background Relay

1. **Atomic Write:** `TicketService.createTicket(...)` writes the `Ticket` entity, `TicketHistory`, and calls `outboxService.recordEvent(...)` inside the same `@Transactional` boundary.
2. **Scheduled Poller:** `OutboxPublisherService.pollAndPublish()` runs at fixed 1-second intervals:
   - Fetches pending events (`status = 'PENDING'`).
   - Publishes to Kafka with the tenant ID as the message key: `kafkaTemplate.send(topic, tenantId, payload)`.
   - On acknowledgment, transitions status to `PUBLISHED` with `published_at = now()`.
   - On error, increments `retry_count` and records `error_message`.

---

## 3. Distributed Idempotency Engine

### 3.1 Flow & Invariants

```
Client Request (POST /api/v1/tickets) with [Idempotency-Key: idem-xyz]
   │
   ▼
IdempotencyFilter (Order 2)
   │
   ├─► Query idempotency_records via IdempotencyService (RLS-protected)
   │     │
   │     ├─► Found?
   │     │     └─► Return cached status & payload with [X-Idempotent-Replay: true] (NO duplicate execution!)
   │     │
   │     └─► Not Found?
   │           └─► Proceed down FilterChain to Controller & Service
   │                 │
   │                 ├─► Ticket Created & Outbox Recorded (Status: 201)
   │                 │
   │                 └─► On 2xx Response: Persist (tenant_id, idempotency_key, request_hash, 201, body)
```

### 3.2 Idempotency Service & Transaction Isolation

The `IdempotencyService` is annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)` for caching to guarantee that even if the outer transaction rolls back or commits, the idempotency record is securely isolated and managed with tenant RLS set on the connection.

---

## 4. Verification & Automated Test Results

The test suite `testing/reliability/test-phase4-reliability.ps1` runs 11 rigorous assertions against the running Docker infrastructure:

| Category | Test Case | Expected Result | Status |
|---|---|---|---|
| Health & Liveness | `Actuator_Health_UP` | Spring Boot Actuator `/health` returns `UP` | **PASS** |
| Tenant Isolation | `HTTP_Alpha_Ticket_List` | Alpha receives tickets scoped to `tenant-alpha` | **PASS** |
| Tenant Isolation | `HTTP_Beta_Ticket_List` | Beta receives tickets scoped to `tenant-beta` | **PASS** |
| Security | `HTTP_Cross_Tenant_404` | Beta querying Alpha ticket returns HTTP 404 (Default Deny) | **PASS** |
| Mutation | `HTTP_Create_Ticket` | Ticket created with 201 and full JSON response | **PASS** |
| Distributed Reliability | `Idempotency_Key_Replay` | Replay returns cached body with header `X-Idempotent-Replay: true` | **PASS** |
| Outbox Pattern | `Outbox_Event_Recorded` | Atomic `outbox_events` row exists in `ticket_db` | **PASS** |
| Outbox Relay | `Outbox_Published_Status` | Background publisher updates event status to `PUBLISHED` | **PASS** |
| Kafka Messaging | `Kafka_Message_Received` | Kafka topic `ticket.created.v1` receives canonical event envelope | **PASS** |
| State Machine | `State_Machine_Rejection` | Invalid transition `NEW -> CLOSED` rejected with HTTP 422 Problem JSON | **PASS** |
| State Machine | `Ticket_Assignment_Auto_Open` | Assigning agent transitions ticket `NEW -> OPEN` automatically | **PASS** |

**Summary: 11/11 PASSED (100% Reliability)**
