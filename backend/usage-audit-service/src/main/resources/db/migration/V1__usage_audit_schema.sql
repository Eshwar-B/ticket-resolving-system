-- ==============================================================================
-- Enterprise AI Platform: Usage & Audit Schema & RLS Policies (V1)
-- Database: usage_db
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Append-Only Audit Trail
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    payload JSONB,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_created ON audit_events (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_resource ON audit_events (tenant_id, resource_type, resource_id);

-- 2. Usage Metering Records
CREATE TABLE IF NOT EXISTS usage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    service VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 1,
    unit VARCHAR(32) NOT NULL DEFAULT 'TOKENS',
    cost_estimate NUMERIC(10, 4) NOT NULL DEFAULT 0.0000,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usage_tenant_service ON usage_records (tenant_id, service, recorded_at DESC);

-- 3. Tenant Quotas
CREATE TABLE IF NOT EXISTS tenant_quotas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL UNIQUE,
    monthly_ticket_limit INT NOT NULL DEFAULT 10000,
    monthly_ai_call_limit INT NOT NULL DEFAULT 50000,
    storage_limit_mb INT NOT NULL DEFAULT 5120,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default quotas for development tenants
INSERT INTO tenant_quotas (tenant_id, monthly_ticket_limit, monthly_ai_call_limit, storage_limit_mb)
VALUES 
    ('tenant-alpha', 50000, 200000, 10240),
    ('tenant-beta', 10000, 50000, 2048)
ON CONFLICT (tenant_id) DO NOTHING;

-- ==============================================================================
-- Row-Level Security (RLS) Enforcement
-- ==============================================================================
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_quotas ENABLE ROW LEVEL SECURITY;

CREATE POLICY audit_tenant_isolation ON audit_events
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE POLICY usage_tenant_isolation ON usage_records
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE POLICY quotas_tenant_isolation ON tenant_quotas
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

GRANT ALL ON audit_events TO app_user;
GRANT ALL ON usage_records TO app_user;
GRANT ALL ON tenant_quotas TO app_user;
