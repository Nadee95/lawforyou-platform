package com.lawforyou.document.model;

import lombok.*;

import java.time.Instant;

/**
 * Embedded sub-document representing a single version of an uploaded file.
 * Stored inline inside {@link DocumentMetadata#getVersions()}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {

    /** 1-based version number. */
    private int versionNumber;

    /** MinIO object key: {@code {tenantId}/{documentId}/v{n}/{filename}}. */
    private String minioObjectKey;

    /** File size in bytes. */
    private long fileSizeBytes;

    /** MD5 checksum (ETag returned by MinIO). */
    private String checksum;

    private Instant uploadedAt;

    /** User ID (UUID as string) of the uploader. */
    private String uploadedBy;
}

