package com.enterpriseai.ticket.service;

import com.enterpriseai.ticket.domain.SlaTier;
import com.enterpriseai.ticket.domain.TicketPriority;

/**
 * Maps ticket priority to an SLA tier.
 * In Phase 4, this will read per-tenant SLA configuration from Postgres.
 */
public final class SlaTierResolver {

    private SlaTierResolver() {}

    public static SlaTier resolve(TicketPriority priority) {
        return switch (priority) {
            case CRITICAL, HIGH -> SlaTier.ENTERPRISE;
            case MEDIUM -> SlaTier.PREMIUM;
            case LOW -> SlaTier.STANDARD;
        };
    }
}

