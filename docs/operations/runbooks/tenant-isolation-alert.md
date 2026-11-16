# Runbook: Tenant Isolation Violation Alert

## Symptom
- Prometheus alert: `TenantIsolationViolation` firing (`tenant_isolation_violations_total > 0`).
- Application security logs emitting:
  - `SECURITY WARNING: Cross-tenant data access attempt detected`
  - `SecurityException: Tenant context missing or invalid`
  - PostgreSQL error: `query violates row-level security policy for table ...`

## Impact
- **Severity: P0 / Critical**. Potential cross-tenant data leakage or spoofing attempt.
- The request was blocked by PostgreSQL kernel Row-Level Security (RLS) or application interceptor, but the source of the attempt must be identified and isolated.

## Immediate Response Protocol
1. **Identify the Offending Request:**
   Query the `usage-audit-service` or centralized OpenTelemetry logs using the trace ID:
   ```bash
   # Look for 403 Forbidden or SecurityException events
   docker logs --tail 200 enterprise-ai-usage-audit-service | grep -i "tenant"
   ```
2. **Inspect the Token & Tenant Claim:**
   Verify whether the Keycloak JWT presented in `Authorization: Bearer <token>` contained a valid `tenant_id` claim:
   - Check if the JWT was forged or tampered with.
   - Check if an authorized user from Tenant A attempted to supply Tenant B's UUID in URL path parameters (`/api/v1/tickets/{id}`).
3. **Verify PostgreSQL RLS Enforcer:**
   Confirm that the PostgreSQL session variable was set:
   ```sql
   SELECT current_setting('app.tenant_id', true);
   ```
   Confirm that the `PostgresTenantRlsEnforcer` or `TenantTransactionInterceptor` executed `SET LOCAL app.tenant_id = ?` prior to the query.
4. **Isolate Compromised Account / Tenant:**
   If malicious enumeration or penetration scanning is detected from an authenticated user:
   - Revoke the user session in Keycloak Admin Console (`http://localhost:8080/admin`).
   - Set the user status to `Disabled`.
5. **Post-Mortem & Incident Documentation:**
   - Log the incident in `docs/security/incident-logs/`.
   - Record Tenant ID, Actor ID, Target Entity UUID, Timestamp, and IP address.
