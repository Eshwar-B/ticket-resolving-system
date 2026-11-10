package com.enterpriseai.notification.repository;

import com.enterpriseai.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId, Pageable pageable);
    Optional<Notification> findByIdAndTenantId(UUID id, String tenantId);
}
