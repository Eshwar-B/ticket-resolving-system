package com.enterpriseai.common.redis;

import java.util.Objects;

/**
 * Standardized Redis key generation utility enforcing tenant isolation.
 * Conforms to Blueprint Section 8.2: Scope Redis keys as t:{tenant_id}:{subsystem}:{key}.
 */
public final class TenantRedisKey {

    private TenantRedisKey() {
        // Utility class
    }

    /**
     * Formats a tenant-scoped Redis key.
     * Example: t:tenant-alpha:cache:session-123
     */
    public static String format(String tenantId, String subsystem, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subsystem, "subsystem must not be null");
        Objects.requireNonNull(key, "key must not be null");

        if (tenantId.trim().isEmpty() || subsystem.trim().isEmpty() || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant, subsystem, and key cannot be blank.");
        }

        return String.format("t:%s:%s:%s", tenantId.trim(), subsystem.trim(), key.trim());
    }

    /**
     * Extracts the tenant_id from a formatted key.
     */
    public static String extractTenantId(String redisKey) {
        Objects.requireNonNull(redisKey, "redisKey must not be null");
        String[] parts = redisKey.split(":");
        if (parts.length >= 4 && "t".equals(parts[0])) {
            return parts[1];
        }
        throw new IllegalArgumentException("Invalid tenant-scoped Redis key: " + redisKey);
    }
}

