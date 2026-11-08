package com.enterpriseai.ticket.config;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.common.security.PostgresTenantRlsEnforcer;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * AOP interceptor that sets {@code app.tenant_id} at the start of every
 * {@code @Transactional} method in the ticket-service.
 *
 * <p>This interceptor fires BEFORE Hibernate executes any SQL, so the PostgreSQL
 * RLS policies see the correct {@code current_setting('app.tenant_id', true)} on
 * every query within the transaction.
 *
 * <p><strong>Implementation note:</strong> We unwrap the JDBC Connection from the
 * Hibernate Session and call {@link PostgresTenantRlsEnforcer#setTransactionTenant}.
 * {@code SET LOCAL} in PostgreSQL reverts the setting automatically when the
 * transaction commits or rolls back — there is no cleanup needed.
 */
@Aspect
@Component
public class TenantTransactionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantTransactionInterceptor.class);

    private final EntityManager entityManager;

    public TenantTransactionInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Intercepts all public methods in service classes and sets RLS context.
     * Uses pointcut matching service layer to avoid applying on JPA/Hibernate internals.
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && " +
            "within(com.enterpriseai.ticket.service..*) && " +
            "!within(com.enterpriseai.ticket.service.OutboxPublisherService)")
    public Object setTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = TenantContext.getTenantId().orElse(null);

        if (tenantId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                session.doWork((Connection connection) ->
                        PostgresTenantRlsEnforcer.setTransactionTenant(connection, tenantId)
                );
                log.debug("RLS enforcer: SET LOCAL app.tenant_id = '{}' for method {}",
                        tenantId, joinPoint.getSignature().getName());
            } catch (Exception e) {
                log.warn("Could not set RLS tenant context: {}", e.getMessage());
            }
        } else {
            log.warn("No tenant in context for @Transactional method: {}. RLS default-deny will apply.",
                    joinPoint.getSignature().getName());
        }

        return joinPoint.proceed();
    }
}

