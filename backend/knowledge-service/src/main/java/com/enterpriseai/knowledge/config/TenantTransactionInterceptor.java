package com.enterpriseai.knowledge.config;

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
            "within(com.enterpriseai.knowledge.service..*)")
    public Object setTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = TenantContext.getTenantId().orElse(null);

        if (tenantId != null && !tenantId.isBlank()) {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try {
                    PostgresTenantRlsEnforcer.setTransactionTenant(connection, tenantId);
                    log.debug("RLS enforcer: SET LOCAL app.tenant_id = '{}' for method {}",
                            tenantId, joinPoint.getSignature().getName());
                } catch (Exception e) {
                    log.error("Failed to enforce tenant RLS: {}", e.getMessage(), e);
                    throw new RuntimeException("RLS enforcement failed", e);
                }
            });
        }

        return joinPoint.proceed();
    }
}

