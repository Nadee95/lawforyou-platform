package com.lawforyou.document.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document storing metadata for an uploaded file.
 *
 * <p>Binary content is stored separately in MinIO; this document holds the index
 * and version history. A compound index on {@code (tenantId, caseId)} supports
 * efficient per-case listing with tenant isolation.</p>
 */
@Document(collection = "documents")
@CompoundIndex(name = "tenant_case_idx", def = "{'tenantId': 1, 'caseId': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {

    @Id
    private String id;

    /** Tenant that owns this document — used for all access-control queries. */
    @Indexed
    private UUID tenantId;

    /** Case this document is associated with. */
    @Indexed
    private UUID caseId;

    private String originalFilename;
    private String contentType;
    private DocumentCategory category;
    private String description;

    /** Current (latest) version number. */
    private int currentVersionNumber;

    /** All uploaded versions, ordered by {@link DocumentVersion#getVersionNumber()}. */
    @Builder.Default
    private List<DocumentVersion> versions = new ArrayList<>();

    /** Whether the document has been soft-deleted. */
    @Builder.Default
    private boolean deleted = false;

    private Instant createdAt;
    private Instant updatedAt;

    /** User ID (UUID as string) of the initial uploader. */
    private String createdBy;
}

