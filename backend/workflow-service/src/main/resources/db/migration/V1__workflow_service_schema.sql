-- ==============================================================================
-- Enterprise AI Platform — Workflow Service Schema (V1)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE approval_requests (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT            NOT NULL,
    ticket_id       UUID            NOT NULL,
    action_type     VARCHAR(64)     NOT NULL,
    payload         JSONB           NOT NULL DEFAULT '{}',
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    requester       VARCHAR(128)    NOT NULL,
    approved_by     VARCHAR(128),
    decision_note   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Row-Level Security
ALTER TABLE approval_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_requests FORCE ROW LEVEL SECURITY;

CREATE POLICY approval_requests_tenant_isolation ON approval_requests
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE INDEX idx_approval_requests_tenant_ticket ON approval_requests (tenant_id, ticket_id);
CREATE INDEX idx_approval_requests_tenant_status ON approval_requests (tenant_id, status);
