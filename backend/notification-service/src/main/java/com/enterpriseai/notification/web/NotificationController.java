package com.enterpriseai.notification.web;

import com.enterpriseai.notification.dto.*;
import com.enterpriseai.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications")
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest req) {
        NotificationResponse resp = notificationService.createNotification(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<WebhookResponse> registerWebhook(@Valid @RequestBody WebhookRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.registerWebhook(req));
    }

    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookResponse>> getWebhooks() {
        return ResponseEntity.ok(notificationService.getActiveWebhooks());
    }

    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Void> deactivateWebhook(@PathVariable("id") UUID id) {
        notificationService.deactivateWebhook(id);
        return ResponseEntity.noContent().build();
    }
}
