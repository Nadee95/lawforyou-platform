package com.lawforyou.document.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lawforyou.document.model.DocumentCategory;
import com.lawforyou.document.model.DocumentVersion;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for {@link com.lawforyou.document.model.DocumentMetadata}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDto {

    private String id;
    private UUID tenantId;
    private UUID caseId;
    private String originalFilename;
    private String contentType;
    private DocumentCategory category;
    private String description;
    private int currentVersionNumber;
    private List<DocumentVersion> versions;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}

