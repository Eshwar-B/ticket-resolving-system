-- =============================================================================
-- V3__outbox_and_reliability.sql
-- Phase 4: Event-driven Reliability and Distributed Systems
-- Tables: outbox_events, inbox_events, idempotency_records
-- Database: ticket_db
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. outbox_events — Transactional Outbox pattern
-- ---------------------------------------------------------------------------
-- Guarantees atomic state transition + event publication intent in 1 DB transaction.
-- Outbox publisher polls using SELECT ... FOR UPDATE SKIP LOCKED.

CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      TEXT        NOT NULL,
    topic          TEXT        NOT NULL,
    kafka_key      TEXT        NOT NULL,                             -- Partition key (tenant_id)
    event_type     TEXT        NOT NULL,                             -- e.g. ticket.created.v1
    payload        JSONB       NOT NULL,                             -- Canonical event envelope
    headers        JSONB       NOT NULL DEFAULT '{}',
    status         TEXT        NOT NULL DEFAULT 'PENDING',            -- PENDING, PUBLISHED, FAILED
    retry_count    INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    last_error     TEXT
);

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;

CREATE POLICY outbox_events_tenant_isolation ON outbox_events
    USING (tenant_id = current_setting('app.tenant_id', true) OR current_setting('app.is_system_worker', true) = 'true')
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true) OR current_setting('app.is_system_worker', true) = 'true');

CREATE INDEX idx_outbox_pending ON outbox_events (created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_tenant ON outbox_events (tenant_id);

-- ---------------------------------------------------------------------------
-- 2. inbox_events — Consumer deduplication / idempotent receiver
-- ---------------------------------------------------------------------------
-- Guarantees exactly-once business processing under at-least-once Kafka delivery.

CREATE TABLE inbox_events (
    event_id       UUID        PRIMARY KEY,
    consumer_name  TEXT        NOT NULL,
    tenant_id      TEXT        NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload_hash   TEXT
);

ALTER TABLE inbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox_events FORCE ROW LEVEL SECURITY;

CREATE POLICY inbox_events_tenant_isolation ON inbox_events
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_inbox_consumer ON inbox_events (consumer_name, processed_at DESC);

-- ---------------------------------------------------------------------------
-- 3. idempotency_records — API mutation deduplication
-- ---------------------------------------------------------------------------
-- Idempotency-Key support on mutation APIs: stores request hash and cached response.

CREATE TABLE idempotency_records (
    tenant_id       TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INTEGER     NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '24 hours'),

    CONSTRAINT pk_idempotency_records PRIMARY KEY (tenant_id, idempotency_key)
);

ALTER TABLE idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_records FORCE ROW LEVEL SECURITY;

CREATE POLICY idempotency_records_tenant_isolation ON idempotency_records
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_idempotency_expires ON idempotency_records (expires_at);

-- ---------------------------------------------------------------------------
-- 4. Permissions for app_user
-- ---------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_events       TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON inbox_events        TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_records TO app_user;
