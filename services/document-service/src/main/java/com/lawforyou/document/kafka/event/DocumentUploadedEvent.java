package com.lawforyou.document.kafka.event;

import com.lawforyou.document.model.DocumentCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the {@code document-events} Kafka topic after a successful upload.
 */
public record DocumentUploadedEvent(
        String documentId,
        UUID tenantId,
        UUID caseId,
        String originalFilename,
        DocumentCategory category,
        String uploadedBy,
        int versionNumber,
        Instant uploadedAt
) {}

