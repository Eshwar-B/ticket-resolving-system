-- ==============================================================================
-- Enterprise AI Platform: Identity & Tenant Service Migration (V1)
-- Purpose: Provision Tenant, Membership, Roles, and RLS Policies for identity_db
-- ==============================================================================

-- 1. Create Tenants Table (Not tenant-isolated itself; represents the organizations)
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Users Table (Global identity directory mapped from Keycloak)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY, -- Keycloak User Subject ID (sub)
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(128),
    last_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create Tenant Memberships Table (Tenant-scoped mapping)
CREATE TABLE IF NOT EXISTS user_memberships (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)
);

-- 4. Create Tenant Configurations (Tenant-scoped settings, LLM quotas, feature flags)
CREATE TABLE IF NOT EXISTS tenant_configurations (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    config_key VARCHAR(128) NOT NULL,
    config_value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_config_key UNIQUE (tenant_id, config_key)
);

-- ==============================================================================
-- ROW-LEVEL SECURITY (RLS) POLICIES
-- Invariant: Non-superuser app_user MUST ONLY see rows where tenant_id matches
-- the transaction-scoped session variable 'app.tenant_id'.
-- ==============================================================================

-- Enable RLS on Tenant-Scoped Tables
ALTER TABLE user_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_configurations ENABLE ROW LEVEL SECURITY;

-- Force RLS even for table owners (except superuser)
ALTER TABLE user_memberships FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_configurations FORCE ROW LEVEL SECURITY;

-- Policy for user_memberships
CREATE POLICY tenant_isolation_user_memberships ON user_memberships
    AS PERMISSIVE
    FOR ALL
    TO app_user
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), ''));

-- Policy for tenant_configurations
CREATE POLICY tenant_isolation_tenant_configurations ON tenant_configurations
    AS PERMISSIVE
    FOR ALL
    TO app_user
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), ''));

-- Grant access on tables to app_user
GRANT SELECT, INSERT, UPDATE, DELETE ON tenants TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON users TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_memberships TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_configurations TO app_user;

-- Seed initial test tenants
INSERT INTO tenants (id, name, status, tier) VALUES
('tenant-alpha', 'Alpha Corporation', 'ACTIVE', 'ENTERPRISE'),
('tenant-beta', 'Beta Global Ltd', 'ACTIVE', 'STANDARD')
ON CONFLICT (id) DO NOTHING;

-- Seed initial users (Alice & Bob)
INSERT INTO users (id, email, first_name, last_name, status) VALUES
('usr-alice-alpha', 'alice@alpha.com', 'Alice', 'AlphaAdmin', 'ACTIVE'),
('usr-bob-beta', 'bob@beta.com', 'Bob', 'BetaAdmin', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Seed tenant memberships
INSERT INTO user_memberships (id, tenant_id, user_id, role) VALUES
('mem-001', 'tenant-alpha', 'usr-alice-alpha', 'TENANT_ADMIN'),
('mem-002', 'tenant-beta', 'usr-bob-beta', 'TENANT_ADMIN')
ON CONFLICT (id) DO NOTHING;

-- Seed initial tenant configurations
INSERT INTO tenant_configurations (id, tenant_id, config_key, config_value) VALUES
('cfg-001', 'tenant-alpha', 'llm_quota', '{"monthly_tokens": 5000000, "allow_rag": true}'::jsonb),
('cfg-002', 'tenant-beta', 'llm_quota', '{"monthly_tokens": 1000000, "allow_rag": false}'::jsonb)
ON CONFLICT (id) DO NOTHING;
