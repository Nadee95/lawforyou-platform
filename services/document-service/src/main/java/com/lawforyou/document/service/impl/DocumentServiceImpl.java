package com.lawforyou.document.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.document.dto.request.UploadDocumentRequest;
import com.lawforyou.document.dto.response.DocumentDto;
import com.lawforyou.document.kafka.event.DocumentUploadedEvent;
import com.lawforyou.document.model.DocumentMetadata;
import com.lawforyou.document.model.DocumentVersion;
import com.lawforyou.document.model.OutboxEvent;
import com.lawforyou.document.repository.DocumentMetadataRepository;
import com.lawforyou.document.repository.OutboxEventRepository;
import com.lawforyou.document.service.DocumentService;
import com.lawforyou.document.service.MinioStorageService;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.exception.BusinessException;
import com.nadeex.spring.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of {@link DocumentService}.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Store binary in MinIO.</li>
 *   <li>Upsert {@link DocumentMetadata} in MongoDB.</li>
 *   <li>Persist {@link OutboxEvent} (status=PENDING) for Kafka relay.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final OutboxEventRepository      outboxEventRepository;
    private final MinioStorageService        minioStorageService;
    private final ObjectMapper               objectMapper;

    // ── Upload ───────────────────────────────────────────────────────────────

    @Override
    public DocumentDto uploadDocument(UUID tenantId, String uploadedBy,
                                      UploadDocumentRequest request, MultipartFile file) {
        String filename    = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        // Determine if this is a new document or a new version
        // For simplicity, each upload creates a brand-new document record.
        // (Versioning an existing doc would require a separate "addVersion" endpoint.)
        int versionNumber = 1;
        String tempId = UUID.randomUUID().toString(); // placeholder for object key before save

        // 1. Upload binary to MinIO
        String objectKey;
        try (InputStream is = file.getInputStream()) {
            objectKey = minioStorageService.upload(
                    tenantId, tempId, versionNumber, filename, is, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BusinessException("Failed to read uploaded file: " + e.getMessage());
        }

        // 2. Build and persist document metadata in MongoDB
        Instant now = Instant.now();
        DocumentVersion version = DocumentVersion.builder()
                .versionNumber(versionNumber)
                .minioObjectKey(objectKey)
                .fileSizeBytes(file.getSize())
                .uploadedAt(now)
                .uploadedBy(uploadedBy)
                .build();

        DocumentMetadata metadata = DocumentMetadata.builder()
                .tenantId(tenantId)
                .caseId(request.getCaseId())
                .originalFilename(filename)
                .contentType(contentType)
                .category(request.getCategory())
                .description(request.getDescription())
                .currentVersionNumber(versionNumber)
                .createdBy(uploadedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
        metadata.getVersions().add(version);

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        log.info("DocumentService: saved document '{}' for tenant '{}'", saved.getId(), tenantId);

        // 3. Write outbox event (same MongoDB write — no distributed transaction needed)
        persistOutboxEvent(saved);

        return toDto(saved);
    }

    // ── Query ────────────────────────────────────────────────────────────────

    @Override
    public PagedResponse<DocumentDto> listByCase(UUID tenantId, UUID caseId, Pageable pageable) {
        Page<DocumentDto> page = documentMetadataRepository
                .findByCaseIdAndTenantIdAndDeletedFalse(caseId, tenantId, pageable)
                .map(this::toDto);
        return PagedResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public DocumentDto findById(UUID tenantId, String documentId) {
        return toDto(requireDocument(tenantId, documentId));
    }

    // ── Download ─────────────────────────────────────────────────────────────

    @Override
    public InputStream downloadLatest(UUID tenantId, String documentId) {
        DocumentMetadata doc = requireDocument(tenantId, documentId);
        String objectKey = getVersionOrThrow(doc, doc.getCurrentVersionNumber()).getMinioObjectKey();
        return minioStorageService.download(objectKey);
    }

    @Override
    public InputStream downloadVersion(UUID tenantId, String documentId, int versionNumber) {
        DocumentMetadata doc = requireDocument(tenantId, documentId);
        String objectKey = getVersionOrThrow(doc, versionNumber).getMinioObjectKey();
        return minioStorageService.download(objectKey);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    public void deleteDocument(UUID tenantId, String documentId) {
        DocumentMetadata doc = requireDocument(tenantId, documentId);
        doc.setDeleted(true);
        doc.setUpdatedAt(Instant.now());
        documentMetadataRepository.save(doc);
        log.info("DocumentService: soft-deleted document '{}'", documentId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private DocumentMetadata requireDocument(UUID tenantId, String documentId) {
        return documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    private DocumentVersion getVersionOrThrow(DocumentMetadata doc, int versionNumber) {
        return doc.getVersions().stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("DocumentVersion",
                        doc.getId() + "/v" + versionNumber));
    }

    private void persistOutboxEvent(DocumentMetadata saved) {
        DocumentVersion latestVersion = saved.getVersions().get(saved.getVersions().size() - 1);
        DocumentUploadedEvent event = new DocumentUploadedEvent(
                saved.getId(),
                saved.getTenantId(),
                saved.getCaseId(),
                saved.getOriginalFilename(),
                saved.getCategory(),
                latestVersion.getUploadedBy(),
                latestVersion.getVersionNumber(),
                latestVersion.getUploadedAt()
        );
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("Document")
                    .aggregateId(saved.getId())
                    .eventType(DocumentUploadedEvent.class.getName())
                    .payload(payload)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            log.error("DocumentService: failed to serialize outbox event for document '{}': {}",
                    saved.getId(), e.getMessage());
        }
    }

    private DocumentDto toDto(DocumentMetadata m) {
        return DocumentDto.builder()
                .id(m.getId())
                .tenantId(m.getTenantId())
                .caseId(m.getCaseId())
                .originalFilename(m.getOriginalFilename())
                .contentType(m.getContentType())
                .category(m.getCategory())
                .description(m.getDescription())
                .currentVersionNumber(m.getCurrentVersionNumber())
                .versions(m.getVersions())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}

