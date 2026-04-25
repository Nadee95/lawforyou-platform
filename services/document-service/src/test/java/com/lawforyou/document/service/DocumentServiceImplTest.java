package com.lawforyou.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.document.dto.request.UploadDocumentRequest;
import com.lawforyou.document.dto.response.DocumentDto;
import com.lawforyou.document.model.DocumentCategory;
import com.lawforyou.document.model.DocumentMetadata;
import com.lawforyou.document.model.DocumentVersion;
import com.lawforyou.document.model.OutboxEvent;
import com.lawforyou.document.repository.DocumentMetadataRepository;
import com.lawforyou.document.repository.OutboxEventRepository;
import com.lawforyou.document.service.impl.DocumentServiceImpl;
import com.nadeex.spring.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nadeex.spring.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private DocumentMetadataRepository documentMetadataRepository;
    @Mock private OutboxEventRepository      outboxEventRepository;
    @Mock private MinioStorageService        minioStorageService;
    @InjectMocks private DocumentServiceImpl documentService;

    private ObjectMapper objectMapper;
    private UUID tenantId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        // Inject real ObjectMapper via reflection (Mockito doesn't inject it since it's not a mock)
        try {
            var field = DocumentServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(documentService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tenantId = UUID.randomUUID();
        caseId   = UUID.randomUUID();
    }

    // ── uploadDocument ────────────────────────────────────────────────────────

    @Test
    void uploadDocument_success_savesMetadataAndOutboxEvent() {
        // Arrange
        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setCaseId(caseId);
        request.setCategory(DocumentCategory.CONTRACT);
        request.setDescription("Test contract");

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "pdf-content".getBytes());

        when(minioStorageService.upload(any(), anyString(), anyInt(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("mock-object-key");

        DocumentMetadata saved = DocumentMetadata.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .caseId(caseId)
                .originalFilename("contract.pdf")
                .contentType("application/pdf")
                .category(DocumentCategory.CONTRACT)
                .currentVersionNumber(1)
                .build();
        saved.getVersions().add(com.lawforyou.document.model.DocumentVersion.builder()
                .versionNumber(1).minioObjectKey("mock-object-key")
                .uploadedBy("uploader-id").uploadedAt(java.time.Instant.now()).build());
        when(documentMetadataRepository.save(any())).thenReturn(saved);

        // Act
        DocumentDto result = documentService.uploadDocument(tenantId, "uploader-id", request, file);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCaseId()).isEqualTo(caseId);
        assertThat(result.getCategory()).isEqualTo(DocumentCategory.CONTRACT);

        verify(minioStorageService).upload(eq(tenantId), anyString(), eq(1),
                eq("contract.pdf"), any(), anyLong(), eq("application/pdf"));
        verify(documentMetadataRepository).save(any(DocumentMetadata.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getAggregateType()).isEqualTo("Document");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getEventType()).contains("DocumentUploadedEvent");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_wrongTenant_throwsResourceNotFoundException() {
        UUID otherTenant = UUID.randomUUID();
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse(anyString(), eq(otherTenant)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(otherTenant, "some-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_existingDocument_returnsDto() {
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1")
                .tenantId(tenantId)
                .caseId(caseId)
                .originalFilename("test.pdf")
                .category(DocumentCategory.EVIDENCE)
                .currentVersionNumber(1)
                .build();
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("doc-1", tenantId))
                .thenReturn(Optional.of(doc));

        DocumentDto result = documentService.findById(tenantId, "doc-1");

        assertThat(result.getId()).isEqualTo("doc-1");
        assertThat(result.getCategory()).isEqualTo(DocumentCategory.EVIDENCE);
    }

    // ── deleteDocument ────────────────────────────────────────────────────────

    @Test
    void deleteDocument_existingDocument_setsDeletedTrue() {
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1")
                .tenantId(tenantId)
                .deleted(false)
                .build();
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("doc-1", tenantId))
                .thenReturn(Optional.of(doc));
        when(documentMetadataRepository.save(any())).thenReturn(doc);

        documentService.deleteDocument(tenantId, "doc-1");

        assertThat(doc.isDeleted()).isTrue();
        verify(documentMetadataRepository).save(doc);
    }

    // ── uploadDocument — failure cases ────────────────────────────────────────

    @Test
    void uploadDocument_whenMinioFails_throwsBusinessException() {
        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setCaseId(caseId);
        request.setCategory(DocumentCategory.CONTRACT);

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "content".getBytes());

        when(minioStorageService.upload(any(), anyString(), anyInt(), anyString(), any(), anyLong(), anyString()))
                .thenThrow(new BusinessException("MinIO unavailable"));

        assertThatThrownBy(() -> documentService.uploadDocument(tenantId, "uploader", request, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MinIO unavailable");

        verify(documentMetadataRepository, never()).save(any());
    }

    @Test
    void uploadDocument_whenFileReadFails_throwsBusinessException() throws IOException {
        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setCaseId(caseId);
        request.setCategory(DocumentCategory.CONTRACT);

        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.getOriginalFilename()).thenReturn("contract.pdf");
        when(badFile.getContentType()).thenReturn("application/pdf");
        // getSize() is never reached — IOException is thrown before upload() is called
        when(badFile.getInputStream()).thenThrow(new IOException("disk read error"));

        assertThatThrownBy(() -> documentService.uploadDocument(tenantId, "uploader", request, badFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to read uploaded file");
    }

    // ── downloadLatest ────────────────────────────────────────────────────────

    @Test
    void downloadLatest_existingDocument_returnsStream() {
        DocumentVersion version = DocumentVersion.builder()
                .versionNumber(1).minioObjectKey("tenant/doc/v1/file.pdf").uploadedAt(Instant.now()).build();
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1").tenantId(tenantId).currentVersionNumber(1).build();
        doc.getVersions().add(version);

        InputStream stream = new ByteArrayInputStream("pdf-bytes".getBytes());
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("doc-1", tenantId))
                .thenReturn(Optional.of(doc));
        when(minioStorageService.download("tenant/doc/v1/file.pdf")).thenReturn(stream);

        InputStream result = documentService.downloadLatest(tenantId, "doc-1");

        assertThat(result).isSameAs(stream);
        verify(minioStorageService).download("tenant/doc/v1/file.pdf");
    }

    @Test
    void downloadLatest_documentNotFound_throwsResourceNotFoundException() {
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("missing", tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.downloadLatest(tenantId, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── downloadVersion ───────────────────────────────────────────────────────

    @Test
    void downloadVersion_existingVersion_returnsStream() {
        DocumentVersion v1 = DocumentVersion.builder()
                .versionNumber(1).minioObjectKey("tenant/doc/v1/file.pdf").uploadedAt(Instant.now()).build();
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1").tenantId(tenantId).currentVersionNumber(1).build();
        doc.getVersions().add(v1);

        InputStream stream = new ByteArrayInputStream("v1-bytes".getBytes());
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("doc-1", tenantId))
                .thenReturn(Optional.of(doc));
        when(minioStorageService.download("tenant/doc/v1/file.pdf")).thenReturn(stream);

        InputStream result = documentService.downloadVersion(tenantId, "doc-1", 1);

        assertThat(result).isSameAs(stream);
    }

    @Test
    void downloadVersion_versionNotFound_throwsResourceNotFoundException() {
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1").tenantId(tenantId).currentVersionNumber(1).build();
        // no versions added → version 99 does not exist

        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("doc-1", tenantId))
                .thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.downloadVersion(tenantId, "doc-1", 99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteDocument — not found ────────────────────────────────────────────

    @Test
    void deleteDocument_notFound_throwsResourceNotFoundException() {
        when(documentMetadataRepository.findByIdAndTenantIdAndDeletedFalse("gone", tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(tenantId, "gone"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── listByCase ────────────────────────────────────────────────────────────

    @Test
    void listByCase_returnsPagedResponse() {
        var pageable = PageRequest.of(0, 10);
        DocumentMetadata doc = DocumentMetadata.builder()
                .id("doc-1").tenantId(tenantId).caseId(caseId)
                .originalFilename("brief.pdf").contentType("application/pdf")
                .category(DocumentCategory.PLEADING).currentVersionNumber(1).build();
        Page<DocumentMetadata> page = new PageImpl<>(List.of(doc), pageable, 1);

        when(documentMetadataRepository.findByCaseIdAndTenantIdAndDeletedFalse(caseId, tenantId, pageable))
                .thenReturn(page);

        var result = documentService.listByCase(tenantId, caseId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOriginalFilename()).isEqualTo("brief.pdf");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}

