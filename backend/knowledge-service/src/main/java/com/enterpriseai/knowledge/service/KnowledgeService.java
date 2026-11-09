package com.enterpriseai.knowledge.service;

import com.enterpriseai.common.context.TenantContext;
import com.enterpriseai.knowledge.dto.*;
import com.enterpriseai.knowledge.entity.Document;
import com.enterpriseai.knowledge.entity.DocumentChunk;
import com.enterpriseai.knowledge.repository.DocumentChunkRepository;
import com.enterpriseai.knowledge.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int DEFAULT_CHUNK_SIZE = 400;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    private static final int RRF_K = 60;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final MinioStorageService minioService;
    private final QdrantVectorService qdrantService;

    public KnowledgeService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            MinioStorageService minioService,
            QdrantVectorService qdrantService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.minioService = minioService;
        this.qdrantService = qdrantService;
    }

    @Transactional
    public DocumentResponse ingestDocument(DocumentUploadRequest request) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        byte[] contentBytes = request.content().getBytes(StandardCharsets.UTF_8);
        String contentHash = computeHash(contentBytes);

        // Check if identical document content already exists for this tenant
        Optional<Document> existing = documentRepository.findByTenantIdAndContentHash(tenantId, contentHash);
        if (existing.isPresent()) {
            Document doc = existing.get();
            if (doc.getDeletedAt() != null) {
                doc.setDeletedAt(null);
                doc.setStatus("INDEXED");
                documentRepository.save(doc);
                log.info("Reactivated soft-deleted document id={}", doc.getId());
            } else {
                log.info("Document with identical hash already exists for tenant={}", tenantId);
            }
            return DocumentResponse.from(doc);
        }

        UUID docId = UUID.randomUUID();
        String safeTitle = request.title().replaceAll("[^a-zA-Z0-9.-]", "_");
        String s3Key = minioService.buildS3Key(tenantId, docId.toString(), 1, safeTitle + ".txt");

        // 1. Store raw content in MinIO
        minioService.uploadObject(s3Key, contentBytes, "text/plain");

        // 2. Create Document metadata
        Document document = new Document();
        document.setId(docId);
        document.setTenantId(tenantId);
        document.setTitle(request.title());
        document.setContentType("text/plain");
        document.setSourceType(request.sourceType() != null ? request.sourceType() : "UPLOAD");
        document.setS3Key(s3Key);
        document.setFileSizeBytes((long) contentBytes.length);
        document.setContentHash(contentHash);
        document.setVersion(1);
        if (request.permissions() != null && request.permissions().length > 0) {
            document.setPermissions(request.permissions());
        }
        document.setStatus("INDEXED");

        // 3. Chunk text recursively
        List<String> chunks = chunkText(request.content(), DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
        int chunkIdx = 0;

        for (String chunkText : chunks) {
            UUID chunkId = UUID.randomUUID();
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(chunkId);
            chunk.setTenantId(tenantId);
            chunk.setDocument(document);
            chunk.setVersion(1);
            chunk.setChunkIndex(chunkIdx);
            chunk.setContent(chunkText);
            chunk.setTokenCount(chunkText.split("\\s+").length);
            chunk.setEmbeddingId(chunkId.toString());

            document.getChunks().add(chunk);

            // Index point in Qdrant with tenant isolation payload
            qdrantService.upsertPoint(chunkId, tenantId, docId, chunkId, chunkIdx, chunkText);
            chunkIdx++;
        }

        Document saved = documentRepository.save(document);
        log.info("Ingested and indexed document: id={} title='{}' chunks={} tenant={}",
                saved.getId(), saved.getTitle(), saved.getChunks().size(), tenantId);

        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public HybridSearchResponse searchKnowledge(HybridSearchRequest request) {
        String tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context missing"));

        int limit = request.limit() != null && request.limit() > 0 ? Math.min(request.limit(), 20) : 5;
        String query = request.query();

        // 1. Keyword search via PostgreSQL Full-Text Search
        List<DocumentChunk> ftsChunks = chunkRepository.searchByFts(tenantId, query, limit * 2);

        // 2. Semantic vector search via Qdrant with mandatory tenant_id filter
        List<QdrantVectorService.VectorSearchResult> vectorHits =
                qdrantService.searchVectors(tenantId, query, limit * 2);

        // 3. Reciprocal Rank Fusion (RRF)
        Map<UUID, Double> rrfScores = new HashMap<>();
        Map<UUID, String> retrievalMethods = new HashMap<>();

        // Rank FTS results
        for (int rank = 0; rank < ftsChunks.size(); rank++) {
            UUID chunkId = ftsChunks.get(rank).getId();
            double score = 1.0 / (RRF_K + rank + 1);
            rrfScores.merge(chunkId, score, Double::sum);
            retrievalMethods.put(chunkId, "FTS");
        }

        // Rank Vector results
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            UUID chunkId = vectorHits.get(rank).chunkId();
            double score = 1.0 / (RRF_K + rank + 1);
            rrfScores.merge(chunkId, score, Double::sum);
            retrievalMethods.merge(chunkId, "VECTOR", (existing, newVal) -> "HYBRID");
        }

        // Sort by fused score
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<SearchResultItem> items = new ArrayList<>();
        int count = 0;

        for (Map.Entry<UUID, Double> entry : sorted) {
            if (count >= limit) break;
            UUID chunkId = entry.getKey();
            double score = entry.getValue();

            Optional<DocumentChunk> chunkOpt = chunkRepository.findById(chunkId);
            if (chunkOpt.isPresent()) {
                DocumentChunk chunk = chunkOpt.get();
                Document doc = chunk.getDocument();
                if (doc == null || doc.getDeletedAt() != null || "ARCHIVED".equalsIgnoreCase(doc.getStatus())) {
                    continue;
                }
                String citation = String.format("[Doc: \"%s\" v%d #chunk-%d]",
                        doc.getTitle(), chunk.getVersion(), chunk.getChunkIndex());

                items.add(new SearchResultItem(
                        chunk.getId(),
                        doc.getId(),
                        doc.getTitle(),
                        chunk.getVersion(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        score,
                        retrievalMethods.get(chunkId),
                        citation
                ));
                count++;
            }
        }

        log.info("Hybrid search for query='{}' returned {} fused results for tenant={}",
                query, items.size(), tenantId);

        return new HybridSearchResponse(query, tenantId, items.size(), items);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(int page, int size) {
        return documentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(DocumentResponse::from);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        document.setDeletedAt(OffsetDateTime.now());
        document.setStatus("ARCHIVED");
        documentRepository.save(document);
        log.info("Soft-deleted document id={}", documentId);
    }

    // --- Helpers ---

    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        int start = 0;
        int len = text.length();

        while (start < len) {
            int end = Math.min(start + chunkSize, len);
            // Try to break at a paragraph or sentence boundary
            if (end < len) {
                int nextBreak = text.lastIndexOf("\n\n", end);
                if (nextBreak > start) {
                    end = nextBreak + 2;
                } else {
                    int period = text.lastIndexOf(". ", end);
                    if (period > start) {
                        end = period + 2;
                    }
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
            if (end >= len) break;
            start = Math.max(start + 1, end - overlap);
        }

        return result;
    }

    private String computeHash(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
