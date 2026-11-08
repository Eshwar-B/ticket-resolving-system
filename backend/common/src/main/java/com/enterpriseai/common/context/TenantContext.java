package com.enterpriseai.common.context;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe holder for the authenticated tenant context.
 * Strictly populated on the server side from validated Keycloak JWT tokens.
 * Invariant: Never trust client-supplied headers or request body tenant identifiers.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty.");
        }
        CURRENT_TENANT.set(tenantId.trim());
    }

    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static String requireTenantId() {
        return getTenantId().orElseThrow(() -> 
            new IllegalStateException("No authenticated tenant context found on current thread.")
        );
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

