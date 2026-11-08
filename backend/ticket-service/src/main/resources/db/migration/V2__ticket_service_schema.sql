-- =============================================================================
-- V2__ticket_service_schema.sql
-- Ticket Service — Full Schema with Multi-Tenant RLS
-- Database: ticket_db
-- Owner: ticket-service (owns ticket_db exclusively)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. Extensions
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";    -- trigram indexes for full-text search

-- ---------------------------------------------------------------------------
-- 1. Enums
-- ---------------------------------------------------------------------------

CREATE TYPE ticket_status AS ENUM (
    'NEW',
    'OPEN',
    'IN_PROGRESS',
    'WAITING_FOR_CUSTOMER',
    'WAITING_FOR_APPROVAL',
    'RESOLVED',
    'CLOSED',
    'CANCELLED'
);

CREATE TYPE ticket_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

CREATE TYPE ticket_category AS ENUM (
    'TECHNICAL',
    'BILLING',
    'GENERAL',
    'FEATURE_REQUEST',
    'ORDER',
    'REFUND',
    'OTHER'
);

CREATE TYPE comment_visibility AS ENUM (
    'PUBLIC',   -- visible to customer
    'INTERNAL'  -- agent/admin only
);

CREATE TYPE sla_tier AS ENUM (
    'STANDARD',
    'PREMIUM',
    'ENTERPRISE'
);

-- ---------------------------------------------------------------------------
-- 2. customers — tenant-scoped customer registry
-- ---------------------------------------------------------------------------

CREATE TABLE customers (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL,
    external_id     TEXT,                              -- CRM / ERP reference
    email           TEXT        NOT NULL,
    display_name    TEXT        NOT NULL,
    phone           TEXT,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_customers_tenant_email UNIQUE (tenant_id, email)
);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;

CREATE POLICY customers_tenant_isolation ON customers
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- Indexes
CREATE INDEX idx_customers_tenant     ON customers (tenant_id);
CREATE INDEX idx_customers_email_trgm ON customers USING gin (email gin_trgm_ops);
CREATE INDEX idx_customers_meta       ON customers USING gin (metadata);

-- ---------------------------------------------------------------------------
-- 3. tickets — core ticket entity
-- ---------------------------------------------------------------------------

CREATE TABLE tickets (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        TEXT            NOT NULL,
    ticket_number    BIGSERIAL,                         -- human-friendly sequential ID
    customer_id      UUID            NOT NULL REFERENCES customers(id),
    status           ticket_status   NOT NULL DEFAULT 'NEW',
    priority         ticket_priority NOT NULL DEFAULT 'MEDIUM',
    category         ticket_category NOT NULL DEFAULT 'GENERAL',
    subject          TEXT            NOT NULL,
    description      TEXT            NOT NULL,
    tags             TEXT[]          NOT NULL DEFAULT '{}',
    external_ref     TEXT,                              -- e.g. order-id for correlation
    metadata         JSONB           NOT NULL DEFAULT '{}',
    -- Assignment
    assigned_team    TEXT,
    assigned_agent   TEXT,                              -- Keycloak subject UUID
    -- Timestamps
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    -- Idempotency
    idempotency_key  TEXT            UNIQUE,
    -- Soft-delete
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT chk_resolved_at CHECK (
        status NOT IN ('RESOLVED', 'CLOSED') OR resolved_at IS NOT NULL
    )
);

ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets FORCE ROW LEVEL SECURITY;

CREATE POLICY tickets_tenant_isolation ON tickets
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- Indexes
CREATE INDEX idx_tickets_tenant          ON tickets (tenant_id);
CREATE INDEX idx_tickets_tenant_status   ON tickets (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_tickets_tenant_customer ON tickets (tenant_id, customer_id);
CREATE INDEX idx_tickets_tenant_agent    ON tickets (tenant_id, assigned_agent) WHERE assigned_agent IS NOT NULL;
CREATE INDEX idx_tickets_created_at      ON tickets (tenant_id, created_at DESC);
CREATE INDEX idx_tickets_number          ON tickets (ticket_number);
CREATE INDEX idx_tickets_subject_trgm    ON tickets USING gin (subject gin_trgm_ops);
CREATE INDEX idx_tickets_meta            ON tickets USING gin (metadata);

-- ---------------------------------------------------------------------------
-- 4. ticket_comments — threaded comments with visibility control
-- ---------------------------------------------------------------------------

CREATE TABLE ticket_comments (
    id           UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    TEXT               NOT NULL,
    ticket_id    UUID               NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id    TEXT               NOT NULL,  -- Keycloak subject UUID
    author_name  TEXT               NOT NULL,
    visibility   comment_visibility NOT NULL DEFAULT 'INTERNAL',
    body         TEXT               NOT NULL,
    attachments  JSONB              NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ        NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);

ALTER TABLE ticket_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_comments FORCE ROW LEVEL SECURITY;

CREATE POLICY ticket_comments_tenant_isolation ON ticket_comments
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_ticket_comments_ticket   ON ticket_comments (ticket_id, created_at ASC);
CREATE INDEX idx_ticket_comments_tenant   ON ticket_comments (tenant_id);
CREATE INDEX idx_ticket_comments_author   ON ticket_comments (author_id);

-- ---------------------------------------------------------------------------
-- 5. ticket_assignments — assignment history log
-- ---------------------------------------------------------------------------

CREATE TABLE ticket_assignments (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    TEXT        NOT NULL,
    ticket_id    UUID        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    assigned_to  TEXT        NOT NULL,   -- Keycloak subject UUID of agent
    assigned_by  TEXT        NOT NULL,   -- Keycloak subject UUID of assigner
    team         TEXT,
    note         TEXT,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE ticket_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_assignments FORCE ROW LEVEL SECURITY;

CREATE POLICY ticket_assignments_tenant_isolation ON ticket_assignments
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_ticket_assignments_ticket ON ticket_assignments (ticket_id, assigned_at DESC);
CREATE INDEX idx_ticket_assignments_tenant ON ticket_assignments (tenant_id);

-- ---------------------------------------------------------------------------
-- 6. ticket_history — immutable audit trail of every status transition
-- ---------------------------------------------------------------------------

CREATE TABLE ticket_history (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT         NOT NULL,
    ticket_id       UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    actor_id        TEXT         NOT NULL,   -- Keycloak subject UUID
    actor_name      TEXT         NOT NULL,
    event_type      TEXT         NOT NULL,   -- STATUS_CHANGED | ASSIGNED | COMMENTED | PRIORITY_CHANGED | ...
    from_value      TEXT,
    to_value        TEXT,
    details         JSONB        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE ticket_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_history FORCE ROW LEVEL SECURITY;

CREATE POLICY ticket_history_tenant_isolation ON ticket_history
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_ticket_history_ticket ON ticket_history (ticket_id, created_at ASC);
CREATE INDEX idx_ticket_history_tenant ON ticket_history (tenant_id);

-- ---------------------------------------------------------------------------
-- 7. ticket_sla — SLA tracking per ticket
-- ---------------------------------------------------------------------------

CREATE TABLE ticket_sla (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          TEXT        NOT NULL,
    ticket_id          UUID        NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    tier               sla_tier    NOT NULL DEFAULT 'STANDARD',
    -- First response SLA
    first_response_due TIMESTAMPTZ NOT NULL,
    first_response_at  TIMESTAMPTZ,
    first_response_met BOOLEAN,
    -- Resolution SLA
    resolution_due     TIMESTAMPTZ NOT NULL,
    resolution_met     BOOLEAN,
    -- Breach tracking
    breached           BOOLEAN     NOT NULL DEFAULT false,
    paused_at          TIMESTAMPTZ,
    pause_duration_s   BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE ticket_sla ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_sla FORCE ROW LEVEL SECURITY;

CREATE POLICY ticket_sla_tenant_isolation ON ticket_sla
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_ticket_sla_tenant          ON ticket_sla (tenant_id);
CREATE INDEX idx_ticket_sla_breach          ON ticket_sla (tenant_id, breached) WHERE breached = false;
CREATE INDEX idx_ticket_sla_resolution_due  ON ticket_sla (resolution_due) WHERE resolution_met IS NULL;

-- ---------------------------------------------------------------------------
-- 8. Trigger — auto-update updated_at columns
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tickets_updated_at
    BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_ticket_comments_updated_at
    BEFORE UPDATE ON ticket_comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_ticket_sla_updated_at
    BEFORE UPDATE ON ticket_sla
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 9. App role permissions
-- ---------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE, DELETE ON customers         TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON tickets           TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ticket_comments   TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ticket_assignments TO app_user;
GRANT SELECT, INSERT, UPDATE         ON ticket_history    TO app_user;
GRANT SELECT, INSERT, UPDATE         ON ticket_sla        TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public     TO app_user;

-- ---------------------------------------------------------------------------
-- 10. Seed data for testing (Phase 3)
-- ---------------------------------------------------------------------------

-- Use SET LOCAL so this only applies within the current transaction
-- (Flyway runs migrations in a transaction by default)

SET LOCAL app.tenant_id = 'tenant-alpha';

INSERT INTO customers (id, tenant_id, email, display_name, external_id)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'tenant-alpha', 'john.doe@acme.com', 'John Doe', 'CRM-001'),
    ('a1000000-0000-0000-0000-000000000002', 'tenant-alpha', 'jane.smith@acme.com', 'Jane Smith', 'CRM-002')
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO tickets (id, tenant_id, customer_id, status, priority, category, subject, description, idempotency_key)
VALUES
    (
        'b1000000-0000-0000-0000-000000000001',
        'tenant-alpha',
        'a1000000-0000-0000-0000-000000000001',
        'NEW', 'HIGH', 'ORDER',
        'Empty package received — refund requested',
        'I received my order #12345 today but the package was completely empty. I need a refund immediately.',
        'seed-ticket-alpha-001'
    )
ON CONFLICT (idempotency_key) DO NOTHING;

SET LOCAL app.tenant_id = 'tenant-beta';

INSERT INTO customers (id, tenant_id, email, display_name, external_id)
VALUES
    ('c1000000-0000-0000-0000-000000000001', 'tenant-beta', 'bob.jones@betacorp.com', 'Bob Jones', 'CRM-B001')
ON CONFLICT (tenant_id, email) DO NOTHING;

INSERT INTO tickets (id, tenant_id, customer_id, status, priority, category, subject, description, idempotency_key)
VALUES
    (
        'd1000000-0000-0000-0000-000000000001',
        'tenant-beta',
        'c1000000-0000-0000-0000-000000000001',
        'OPEN', 'MEDIUM', 'TECHNICAL',
        'Login page returns 500 error',
        'The login page has been returning HTTP 500 for the last 2 hours. Multiple users affected.',
        'seed-ticket-beta-001'
    )
ON CONFLICT (idempotency_key) DO NOTHING;

