-- ==============================================================================
-- Enterprise AI Platform: SLA Breach Tracking & Escalation (V4)
-- ==============================================================================

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS sla_breach_at TIMESTAMPTZ;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS is_sla_breached BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_tickets_sla_breach ON tickets (tenant_id, is_sla_breached, sla_breach_at);
