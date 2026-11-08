package com.enterpriseai.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ticket Service — Multi-tenant support ticket management.
 *
 * <p>Owns {@code ticket_db} exclusively. Enforces PostgreSQL Row-Level Security
 * on every transaction via {@link com.enterpriseai.common.security.PostgresTenantRlsEnforcer}.
 * Tenant identity is propagated from the upstream Gateway JWT claim into
 * {@link com.enterpriseai.common.context.TenantContext} before each request.
 */
@SpringBootApplication(scanBasePackages = {"com.enterpriseai.ticket", "com.enterpriseai.common"})
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}

