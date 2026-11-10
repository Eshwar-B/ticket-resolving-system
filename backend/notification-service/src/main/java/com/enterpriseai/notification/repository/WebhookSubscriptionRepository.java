package com.enterpriseai.notification.repository;

import com.enterpriseai.notification.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByTenantIdAndIsActiveTrue(String tenantId);
    Optional<WebhookSubscription> findByIdAndTenantId(UUID id, String tenantId);
}
