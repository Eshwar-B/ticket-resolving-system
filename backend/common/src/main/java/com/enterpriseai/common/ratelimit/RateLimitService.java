package com.enterpriseai.common.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tenant-scoped sliding window rate limiter.
 * Keys: t:{tenantId}:rl:{endpoint}:{windowMinute}
 */
public class RateLimitService {

    private final Map<String, AtomicInteger> localCounter = new ConcurrentHashMap<>();

    public boolean isAllowed(String tenantId, String endpoint, int maxRequestsPerMinute) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = String.format("t:%s:rl:%s:%d", tenantId, endpoint, currentMinute);

        AtomicInteger count = localCounter.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        // Evict expired minutes periodically
        if (localCounter.size() > 500) {
            localCounter.keySet().removeIf(k -> {
                try {
                    String[] parts = k.split(":");
                    long win = Long.parseLong(parts[parts.length - 1]);
                    return (currentMinute - win) > 2;
                } catch (Exception e) {
                    return false;
                }
            });
        }

        return current <= maxRequestsPerMinute;
    }

    public int getCurrentCount(String tenantId, String endpoint) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = String.format("t:%s:rl:%s:%d", tenantId, endpoint, currentMinute);
        AtomicInteger count = localCounter.get(key);
        return count != null ? count.get() : 0;
    }
}
