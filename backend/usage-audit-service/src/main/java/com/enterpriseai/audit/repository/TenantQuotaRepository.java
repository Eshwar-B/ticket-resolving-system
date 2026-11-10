package com.enterpriseai.audit.repository;

import com.enterpriseai.audit.entity.TenantQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantQuotaRepository extends JpaRepository<TenantQuota, UUID> {
    Optional<TenantQuota> findByTenantId(String tenantId);
}
