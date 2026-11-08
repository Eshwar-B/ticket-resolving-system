package com.enterpriseai.ticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables AOP for the {@link TenantTransactionInterceptor} RLS aspect.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}

