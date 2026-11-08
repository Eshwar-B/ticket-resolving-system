package com.enterpriseai.ticket.service;

import com.enterpriseai.ticket.entity.IdempotencyRecord;
import com.enterpriseai.ticket.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service managing distributed idempotency records in {@code ticket_db}.
 *
 * <p>Methods run with {@code @Transactional} so that {@link com.enterpriseai.ticket.config.TenantTransactionInterceptor}
 * establishes {@code app.tenant_id} on the JDBC connection before queries execute against RLS-protected
 * {@code idempotency_records}.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findRecord(String tenantId, String idempotencyKey) {
        return repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord(String tenantId, String idempotencyKey, String requestHash, int responseStatus, String responseBody) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setTenantId(tenantId);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setResponseStatus(responseStatus);
        record.setResponseBody(responseBody);
        repository.save(record);
        log.debug("Persisted idempotency record: tenant={} key={} status={}", tenantId, idempotencyKey, responseStatus);
    }
}

