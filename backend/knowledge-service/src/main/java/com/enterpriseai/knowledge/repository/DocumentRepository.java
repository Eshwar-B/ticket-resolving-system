package com.enterpriseai.knowledge.repository;

import com.enterpriseai.knowledge.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    Page<Document> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Optional<Document> findByTenantIdAndContentHash(String tenantId, String contentHash);
}

