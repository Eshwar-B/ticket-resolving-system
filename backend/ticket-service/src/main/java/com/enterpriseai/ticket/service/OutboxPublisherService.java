package com.enterpriseai.ticket.service;

import com.enterpriseai.ticket.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reliable Outbox Publisher Service.
 *
 * <p><strong>Distributed Systems Invariant:</strong>
 * Polling uses {@code FOR UPDATE SKIP LOCKED} to allow multiple concurrent publisher
 * instances without duplicate publishes or row locking contention.
 *
 * <p>Messages are sent to Kafka partitioned by {@code kafkaKey} (which is {@code tenantId}),
 * ensuring FIFO ordering per tenant.
 */
@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);
    private static final int MAX_BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final String DLQ_TOPIC = "ticket.dlq.v1";

    private final EntityManager entityManager;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(EntityManager entityManager,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.entityManager = entityManager;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:2000}", initialDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        try {
            processBatch();
        } catch (Exception e) {
            log.error("Outbox publisher background poll failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processBatch() {
        // Authorize background worker for cross-tenant outbox polling
        entityManager.createNativeQuery("SELECT set_config('app.is_system_worker', 'true', true)").getSingleResult();

        @SuppressWarnings("unchecked")
        List<OutboxEvent> pendingEvents = entityManager.createNativeQuery(
                "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
                OutboxEvent.class)
                .setParameter("limit", MAX_BATCH_SIZE)
                .getResultList();

        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }

        return pendingEvents.size();
    }

    private void publishSingleEvent(OutboxEvent event) {
        String targetTopic = event.getTopic();
        String partitionKey = event.getKafkaKey();
        String payload = event.getPayload();

        try {
            // Synchronous get with timeout to ensure at-least-once before DB commit
            kafkaTemplate.send(targetTopic, partitionKey, payload).get();

            event.setStatus("PUBLISHED");
            event.setPublishedAt(OffsetDateTime.now());
            log.info("Outbox published event id={} topic={} tenant={}",
                    event.getId(), targetTopic, event.getTenantId());
        } catch (Exception ex) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setLastError(ex.getMessage());

            if (retries >= MAX_RETRIES) {
                log.error("Outbox event id={} exceeded max retries ({}). Routing to DLQ: {}",
                        event.getId(), retries, DLQ_TOPIC, ex);
                try {
                    kafkaTemplate.send(DLQ_TOPIC, partitionKey, payload).get();
                    event.setStatus("FAILED");
                } catch (Exception dlqEx) {
                    log.error("Failed to publish to DLQ for event id={}: {}", event.getId(), dlqEx.getMessage());
                }
            } else {
                log.warn("Failed to publish outbox event id={} (attempt {}/{}): {}",
                        event.getId(), retries, MAX_RETRIES, ex.getMessage());
            }
        }
    }
}

