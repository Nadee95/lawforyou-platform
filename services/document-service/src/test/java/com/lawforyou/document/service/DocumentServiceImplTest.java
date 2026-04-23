package com.lawforyou.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.document.dto.request.UploadDocumentRequest;
import com.lawforyou.document.dto.response.DocumentDto;
import com.lawforyou.document.model.DocumentCategory;
import com.lawforyou.document.model.DocumentMetadata;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
}

