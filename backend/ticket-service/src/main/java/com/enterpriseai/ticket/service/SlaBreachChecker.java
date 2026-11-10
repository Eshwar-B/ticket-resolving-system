package com.enterpriseai.ticket.service;

import com.enterpriseai.ticket.domain.TicketStatus;
import com.enterpriseai.ticket.entity.Ticket;
import com.enterpriseai.ticket.entity.TicketSla;
import com.enterpriseai.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SlaBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachChecker.class);
    private final TicketRepository ticketRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SlaBreachChecker(TicketRepository ticketRepository,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.ticketRepository = ticketRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void checkSlaBreaches() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Ticket> activeTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED)
                .toList();

        for (Ticket t : activeTickets) {
            TicketSla sla = t.getSla();
            if (sla != null && !sla.isBreached()) {
                if (sla.getResolutionDue() != null && now.isAfter(sla.getResolutionDue())) {
                    sla.setBreached(true);
                    log.warn("SLA breached for ticket {} (tenant: {})", t.getId(), t.getTenantId());
                    try {
                        String payload = String.format("{\"ticketId\":\"%s\",\"tenantId\":\"%s\",\"breachedAt\":\"%s\"}",
                                t.getId(), t.getTenantId(), now);
                        kafkaTemplate.send("ticket.events.v1", t.getTenantId(), payload);
                    } catch (Exception e) {
                        log.warn("Could not publish SLA breach event: {}", e.getMessage());
                    }
                }
            }
        }
    }
}
