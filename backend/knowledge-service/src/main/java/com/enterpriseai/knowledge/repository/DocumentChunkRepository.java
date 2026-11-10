package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    /**
     * PostgreSQL Full-Text Search with ts_rank ranking.
     * Evaluates plainto_tsquery('english', :query) against the pre-indexed fts_vector.
     */
    @Query(value = """
        SELECT c.* FROM document_chunks c
        WHERE c.tenant_id = :tenantId
          AND c.fts_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(c.fts_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentChunk> searchByFts(
            @Param("tenantId") String tenantId,
            @Param("query") String query,
            @Param("limit") int limit
    );
}

