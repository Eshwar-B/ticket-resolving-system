package com.enterpriseai.notification.service;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.notification.dto.*;
import com.enterpriseai.notification.entity.Notification;
import com.enterpriseai.notification.entity.WebhookSubscription;
import com.enterpriseai.notification.repository.NotificationRepository;
import com.enterpriseai.notification.repository.WebhookSubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WebhookSubscriptionRepository webhookRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               WebhookSubscriptionRepository webhookRepository) {
        this.notificationRepository = notificationRepository;
        this.webhookRepository = webhookRepository;
    }

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest req) {
        String tenantId = TenantContext.getRequiredTenantId();
        Notification n = new Notification(
            tenantId,
            req.userId(),
            req.type(),
            req.title(),
            req.body()
        );
        Notification saved = notificationRepository.save(n);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(String userId, int page, int size) {
        String tenantId = TenantContext.getRequiredTenantId();
        return notificationRepository
            .findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, PageRequest.of(page, size))
            .map(this::mapToResponse);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id) {
        String tenantId = TenantContext.getRequiredTenantId();
        Notification n = notificationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        n.setRead(true);
        Notification saved = notificationRepository.save(n);
        return mapToResponse(saved);
    }

    @Transactional
    public WebhookResponse registerWebhook(WebhookRequest req) {
        String tenantId = TenantContext.getRequiredTenantId();
        String secretHash = hashSecret(req.getSecret());
        WebhookSubscription sub = new WebhookSubscription(tenantId, req.getUrl(), secretHash);
        WebhookSubscription saved = webhookRepository.save(sub);
        return new WebhookResponse(saved.getId(), saved.getTenantId(), saved.getUrl(), saved.isActive(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> getActiveWebhooks() {
        String tenantId = TenantContext.getRequiredTenantId();
        return webhookRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(w -> new WebhookResponse(w.getId(), w.getTenantId(), w.getUrl(), w.isActive(), w.getCreatedAt()))
            .toList();
    }

    @Transactional
    public void deactivateWebhook(UUID id) {
        String tenantId = TenantContext.getRequiredTenantId();
        WebhookSubscription sub = webhookRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found: " + id));
        sub.setActive(false);
        webhookRepository.save(sub);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getTenantId(),
            n.getUserId(),
            n.getType(),
            n.getTitle(),
            n.getBody(),
            n.isRead(),
            n.getCreatedAt(),
            n.getReadAt()
        );
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
