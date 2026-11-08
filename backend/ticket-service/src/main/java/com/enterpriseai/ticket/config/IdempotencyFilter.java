package com.enterpriseai.ticket.config;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.ticket.entity.IdempotencyRecord;
import com.enterpriseai.ticket.repository.IdempotencyRecordRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Filter providing distributed idempotency protection via {@code Idempotency-Key}.
 *
 * <p><strong>Engineering Invariant:</strong>
 * Guarantees that retrying a mutation with the exact same Idempotency-Key returns
 * the original response without executing duplicate business effects.
 */
@Component
@Order(2) // Runs immediately after TenantContextFilter
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyFilter(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        // Only enforce on state-mutating methods when header is supplied
        if (idempotencyKey == null || idempotencyKey.isBlank() || "GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = TenantContext.getTenantId().orElse("unknown");
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // Check if an existing record exists for (tenantId, idempotencyKey)
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey.trim());

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            log.info("Idempotent replay detected for key={} tenant={}", idempotencyKey, tenantId);
            response.setStatus(record.getResponseStatus());
            response.setContentType("application/json");
            response.setHeader("X-Idempotent-Replay", "true");
            response.getWriter().write(record.getResponseBody());
            response.getWriter().flush();
            return;
        }

        // Process actual request
        filterChain.doFilter(wrappedRequest, wrappedResponse);

        // Save idempotency record on successful creation / mutation (2xx responses)
        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            byte[] requestBody = wrappedRequest.getContentAsByteArray();
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            String requestHash = computeHash(requestBody);
            String responseStr = new String(responseBody, StandardCharsets.UTF_8);

            try {
                IdempotencyRecord newRecord = new IdempotencyRecord();
                newRecord.setTenantId(tenantId);
                newRecord.setIdempotencyKey(idempotencyKey.trim());
                newRecord.setRequestHash(requestHash);
                newRecord.setResponseStatus(status);
                newRecord.setResponseBody(responseStr);
                idempotencyRecordRepository.save(newRecord);
                log.debug("Cached idempotency record for key={} tenant={}", idempotencyKey, tenantId);
            } catch (Exception e) {
                log.warn("Failed to persist idempotency record: {}", e.getMessage());
            }
        }

        wrappedResponse.copyBodyToResponse();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "sha256-error";
        }
    }
}

