package com.lawforyou.document.repository;

import com.lawforyou.document.model.DocumentMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * MongoDB repository for {@link DocumentMetadata}.
 * All queries include {@code tenantId} to enforce tenant isolation.
 */
public interface DocumentMetadataRepository extends MongoRepository<DocumentMetadata, String> {

    Optional<DocumentMetadata> findByIdAndTenantIdAndDeletedFalse(String id, UUID tenantId);

    Page<DocumentMetadata> findByCaseIdAndTenantIdAndDeletedFalse(UUID caseId, UUID tenantId, Pageable pageable);
}

