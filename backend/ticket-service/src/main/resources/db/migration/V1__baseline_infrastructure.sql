-- ==============================================================================
-- Enterprise AI Platform: Phase 1 Foundation Baseline Migration
-- Purpose: Establishes migration baseline and proves Flyway migration execution
-- Note: Does NOT define future Phase 2 (Identity) or Phase 3 (Ticket) business tables.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS platform_infrastructure_status (
    id VARCHAR(64) PRIMARY KEY,
    component_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Grant appropriate permissions to application user
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_infrastructure_status TO app_user;

-- Insert foundation baseline verification record
INSERT INTO platform_infrastructure_status (id, component_name, status, metadata)
VALUES ('infra-phase-1', 'ticket-service-database', 'INITIALIZED', '{"phase": 1, "migrator": "flyway"}'::jsonb)
ON CONFLICT (id) DO NOTHING;

