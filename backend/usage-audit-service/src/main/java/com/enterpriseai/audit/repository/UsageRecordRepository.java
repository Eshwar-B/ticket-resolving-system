package com.enterpriseai.audit.repository;

import com.enterpriseai.audit.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    List<UsageRecord> findByTenantId(String tenantId);

    @Query("SELECT r.service, SUM(r.quantity) FROM UsageRecord r WHERE r.tenantId = :tenantId GROUP BY r.service")
    List<Object[]> sumQuantityByService(@Param("tenantId") String tenantId);
}
