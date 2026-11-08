package com.enterpriseai.common.security;

import com.enterpriseai.common.context.TenantContext;
import org.slf4j.MDC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Ensures PostgreSQL session variable 'app.tenant_id' is transaction-locally
 * populated via 'SET LOCAL app.tenant_id = ?'.
 * 
 * Crucial Invariant: Using SET LOCAL guarantees that when the connection returns
 * to the HikariCP connection pool at transaction commit/rollback, the tenant context
 * is completely discarded, preventing cross-tenant leakage.
 */
public final class PostgresTenantRlsEnforcer {

    private PostgresTenantRlsEnforcer() {
        // Utility class
    }

    /**
     * Executes SET LOCAL app.tenant_id = ? within the active database transaction.
     */
    public static void setTransactionTenant(Connection connection, String tenantId) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Database connection cannot be null.");
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty for RLS.");
        }

        // PostgreSQL does not support parameters in 'SET' statements.
        // We use 'SELECT set_config('app.tenant_id', ?, true)' which is the parameterized,
        // SQL-injection-safe equivalent of 'SET LOCAL app.tenant_id = ?'.
        try (PreparedStatement stmt = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            stmt.setString(1, tenantId.trim());
            stmt.execute();
        }

        // Synchronize with logging MDC for end-to-end trace correlation
        MDC.put("tenant_id", tenantId.trim());
    }

    /**
     * Resets tenant context from MDC after request completion.
     */
    public static void clearMdc() {
        MDC.remove("tenant_id");
    }
}

