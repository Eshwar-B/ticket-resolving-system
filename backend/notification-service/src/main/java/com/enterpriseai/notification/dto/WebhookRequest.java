package com.enterpriseai.notification.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public class WebhookRequest {
    @NotBlank
    private String url;

    @NotBlank
    private String secret;

    public WebhookRequest() {}
    public WebhookRequest(String url, String secret) {
        this.url = url;
        this.secret = secret;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
