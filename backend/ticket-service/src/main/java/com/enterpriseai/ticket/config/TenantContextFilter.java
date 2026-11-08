package com.enterpriseai.ticket.config;

import com.enterpriseai.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts the tenant ID from the {@code X-Tenant-Id}
 * request header (set by the upstream Gateway after JWT validation) and
 * populates {@link TenantContext} for the duration of the request.
 *
 * <p><strong>Security note:</strong> In production, this service must only be
 * reachable via the Gateway, which validates the Keycloak JWT and sets this
 * header after extracting the {@code tenant_id} claim. External clients MUST
 * NOT be able to reach this service directly.
 *
 * <p>For Phase 3 development (Gateway not yet implemented), this filter also
 * accepts a direct header for local testing. The Gateway integration will be
 * implemented in Phase 3 continuation.
 */
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_HEADER);
        String correlationId = request.getHeader(CORRELATION_HEADER);

        try {
            if (tenantId != null && !tenantId.isBlank()) {
                // Sanitize: only allow alphanumeric, hyphens, underscores
                if (!tenantId.matches("[a-zA-Z0-9_-]{1,100}")) {
                    log.warn("Rejected invalid tenant ID format: '{}'", tenantId);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid tenant identifier");
                    return;
                }
                TenantContext.setTenantId(tenantId);
                MDC.put("tenantId", tenantId);
                log.debug("Tenant context set: {}", tenantId);
            } else {
                log.debug("No {} header present — tenant context empty", TENANT_HEADER);
            }

            if (correlationId != null) {
                MDC.put("correlationId", correlationId);
                response.setHeader("X-Correlation-Id", correlationId);
            }

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("correlationId");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip for actuator endpoints
        return request.getRequestURI().startsWith("/actuator");
    }
}

