package com.enterpriseai.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service managing object storage operations against S3 / MinIO.
 * Enforces tenant-isolated object key paths:
 * {@code tenants/{tenantId}/documents/{documentId}/v{version}/{filename}}
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final String endpoint;
    private final String bucket;
    private final HttpClient httpClient;

    public MinioStorageService(
            @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${minio.bucket:enterprise-ai-knowledge}") String bucket) {
        this.endpoint = endpoint.replaceAll("/$", "");
        this.bucket = bucket;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @jakarta.annotation.PostConstruct
    public void ensureBucket() {
        try {
            URI uri = URI.create(endpoint + "/" + bucket);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Ensured S3 bucket '{}' exists (status={})", bucket, response.statusCode());
        } catch (Exception e) {
            log.warn("Could not check/create bucket '{}': {}", bucket, e.getMessage());
        }
    }

    public String buildS3Key(String tenantId, String documentId, int version, String filename) {
        return String.format("tenants/%s/documents/%s/v%d/%s", tenantId, documentId, version, filename);
    }

    public void uploadObject(String s3Key, byte[] data, String contentType) {
        try {
            URI uri = URI.create(endpoint + "/" + bucket + "/" + s3Key);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", contentType != null ? contentType : "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                ensureBucket();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Uploaded S3 object: key={} size={} bytes", s3Key, data.length);
            } else {
                log.warn("S3 upload returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to upload object to S3: key={} error={}", s3Key, e.getMessage(), e);
            throw new RuntimeException("S3 upload failed: " + s3Key, e);
        }
    }

    public byte[] getObject(String s3Key) {
        try {
            URI uri = URI.create(endpoint + "/" + bucket + "/" + s3Key);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                throw new RuntimeException("S3 getObject failed with status " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch object from S3: key={} error={}", s3Key, e.getMessage(), e);
            throw new RuntimeException("S3 getObject failed", e);
        }
    }
}
