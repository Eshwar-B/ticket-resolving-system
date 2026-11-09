package com.enterpriseai.knowledge.web;

import com.enterpriseai.knowledge.dto.*;
import com.enterpriseai.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Management & Hybrid RAG", description = "Tenant-isolated document ingestion and cited hybrid retrieval")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest a new document into MinIO, PostgreSQL FTS, and Qdrant")
    public DocumentResponse ingestDocument(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @Valid @RequestBody DocumentUploadRequest request) {
        return knowledgeService.ingestDocument(request);
    }

    @PostMapping("/search")
    @Operation(summary = "Execute cited hybrid search (PostgreSQL FTS + Qdrant vectors + RRF)")
    public HybridSearchResponse hybridSearch(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @Valid @RequestBody HybridSearchRequest request) {
        return knowledgeService.searchKnowledge(request);
    }

    @GetMapping("/documents")
    @Operation(summary = "List tenant documents")
    public Page<DocumentResponse> listDocuments(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return knowledgeService.listDocuments(page, size);
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document metadata by ID")
    public DocumentResponse getDocument(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PathVariable("id") UUID id) {
        return knowledgeService.getDocument(id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a document")
    public ResponseEntity<Void> deleteDocument(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PathVariable("id") UUID id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
