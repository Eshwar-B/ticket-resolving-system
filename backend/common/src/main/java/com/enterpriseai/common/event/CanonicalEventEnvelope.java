package com.enterpriseai.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard Canonical Event Envelope for all asynchronous messages in the platform.
 * Conforms to {@code contracts/events/envelope.v1.json}.
 *
 * <p><strong>Key Invariant:</strong> Kafka messages are partitioned by {@code tenantId}
 * to guarantee strict FIFO per-tenant ordering while distributing load across partitions.
 */
public record CanonicalEventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        String occurredAt,
        String tenantId,
        String aggregateId,
        int aggregateVersion,
        String correlationId,
        String causationId,
        String traceparent,
        String producer,
        Map<String, Object> payload
) {
    public static CanonicalEventEnvelope create(
            String eventType,
            int eventVersion,
            String tenantId,
            String aggregateId,
            int aggregateVersion,
            String correlationId,
            String causationId,
            String traceparent,
            String producer,
            Map<String, Object> payload
    ) {
        return new CanonicalEventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                eventVersion,
                Instant.now().toString(),
                tenantId,
                aggregateId,
                aggregateVersion,
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                causationId,
                traceparent,
                producer,
                payload
        );
    }
}

