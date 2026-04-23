package com.lawforyou.document.dto.request;

import com.lawforyou.document.model.DocumentCategory;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Metadata accompanying a multipart document upload request.
 * Sent as a JSON {@code @RequestPart("metadata")} alongside the binary file part.
 */
@Data
public class UploadDocumentRequest {

    @NotNull(message = "caseId is required")
    private UUID caseId;

    @NotNull(message = "category is required")
    private DocumentCategory category;

    private String description;
}

