package com.enterpriseai.workflow.config;

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

@Aspect
@Component
public class TenantTransactionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantTransactionInterceptor.class);
    private final EntityManager entityManager;

    public TenantTransactionInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && " +
            "within(com.enterpriseai.workflow.service..*)")
    public Object enforceTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = TenantContext.getTenantId().orElse(null);

        if (tenantId != null && !tenantId.isBlank()) {
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
