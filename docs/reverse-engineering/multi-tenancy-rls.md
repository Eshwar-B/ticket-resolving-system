# Reverse Engineering: Strict Multi-Tenancy via PostgreSQL Row-Level Security (RLS)

## 1. Architectural Motivation
Multi-tenancy in enterprise platforms typically falls into one of three models:
1. **Database-per-tenant:** High isolation, but huge resource overhead (impractical under a 16 GB RAM budget and ₹0 cost).
2. **Schema-per-tenant:** Moderate isolation, but connection pool exhaustion, schema migration sprawl across hundreds of schemas.
3. **Shared database, Shared schema with PostgreSQL Kernel RLS:** Maximum hardware efficiency, unified connection pooling, guaranteed isolation enforced directly by the database kernel, preventing accidental data leakage even if an application bug omits `WHERE tenant_id = ?`.

This platform implements **Model 3 with Defense-in-Depth**.

---

## 2. Kernel Enforcement Mechanism

Every multi-tenant table (e.g. `tickets`, `ticket_comments`, `knowledge_documents`, `workflow_instances`, `notifications`, `audit_events`) has:
1. A mandatory `tenant_id VARCHAR(64) NOT NULL` column.
2. Kernel Row-Level Security enabled:
   ```sql
   ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
   ALTER TABLE tickets FORCE ROW LEVEL SECURITY;
   ```
   > **Note on `FORCE ROW LEVEL SECURITY`:** By default in PostgreSQL, table owners bypass RLS policies. Applying `FORCE ROW LEVEL SECURITY` guarantees that even table owners are strictly evaluated against the policy.
3. The canonical tenant isolation policy:
   ```sql
   CREATE POLICY tenant_isolation_policy ON tickets
     AS RESTRICTIVE
     FOR ALL
     USING (tenant_id = current_setting('app.tenant_id', true))
     WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
   ```

---

## 3. Java Spring Boot Interceptor Pipeline

1. **JWT Verification:** When an HTTP request enters the gateway or microservice, the Keycloak JWT is validated.
2. **Server-Side Derivation:** `tenant_id` is extracted from validated JWT claims (`JwtAuthenticationFilter`). Any user-supplied path variable or body attribute claiming a different tenant is rejected.
3. **ThreadLocal Context:** `TenantContext.setTenantId(tenantId)` stores the active tenant on the processing thread.
4. **Transaction Interceptor (AOP / Connection Hook):** Before any JPA/Hibernate SQL statement executes on the HikariCP connection, `TenantTransactionInterceptor` executes:
   ```java
   entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
       .setParameter("tenantId", tenantId)
       .getSingleResult();
   ```
   The `true` argument sets the configuration locally for the current transaction only (`is_local = true`). When the transaction commits or rolls back, PostgreSQL automatically clears the variable, preventing pool leakage across threads.

---

## 4. Verification Evidence
Verified by 5 isolation drills in `testing/phase2/test-phase2-isolation.ps1`:
- Cross-tenant SELECT returns 0 rows.
- Cross-tenant INSERT is rejected with RLS violation.
- Missing tenant context defaults to deny-all.
- Recycled connection pool handles subsequent requests without leakage.
