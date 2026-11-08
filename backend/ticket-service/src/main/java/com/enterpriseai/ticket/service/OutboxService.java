package com.enterpriseai.ticket.service;

import com.enterpriseai.common.event.CanonicalEventEnvelope;
import com.enterpriseai.ticket.entity.OutboxEvent;
import com.enterpriseai.ticket.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to record outbox events atomically within the active domain transaction.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final String PRODUCER_NAME = "ticket-service";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void recordEvent(String tenantId,
                            String topic,
                            String eventType,
                            int eventVersion,
                            String aggregateId,
                            int aggregateVersion,
                            String correlationId,
                            Map<String, Object> payload) {
        try {
            CanonicalEventEnvelope envelope = CanonicalEventEnvelope.create(
                    eventType,
                    eventVersion,
                    tenantId,
                    aggregateId,
                    aggregateVersion,
                    correlationId,
                    null,
                    null,
                    PRODUCER_NAME,
                    payload
            );

            String jsonPayload = objectMapper.writeValueAsString(envelope);

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTenantId(tenantId);
            outboxEvent.setTopic(topic);
            outboxEvent.setKafkaKey(tenantId); // Partition key is strictly tenantId
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(jsonPayload);
            outboxEvent.setStatus("PENDING");

            outboxEventRepository.save(outboxEvent);
            log.debug("Recorded transactional outbox event: type={} aggregateId={} tenant={}",
                    eventType, aggregateId, tenantId);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Outbox event persistence failed", e);
        }
    }
}

