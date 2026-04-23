package com.lawforyou.document.service;

import com.lawforyou.document.dto.request.UploadDocumentRequest;
import com.lawforyou.document.dto.response.DocumentDto;
import com.nadeex.spring.common.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Core business operations for document management.
 */
public interface DocumentService {

    /**
     * Uploads a new file (or a new version of an existing document) and persists metadata.
     *
     * @param tenantId   owning tenant
     * @param uploadedBy user ID of the uploader (as string)
     * @param request    document metadata from the request part
     * @param file       binary file from the multipart request
     * @return DTO of the created/updated document metadata
     */
    DocumentDto uploadDocument(UUID tenantId, String uploadedBy, UploadDocumentRequest request, MultipartFile file);

    /**
     * Returns a paginated list of documents for a given case.
     *
     * @param tenantId owning tenant
     * @param caseId   case to list documents for
     * @param pageable pagination and sort parameters
     * @return paged response
     */
    PagedResponse<DocumentDto> listByCase(UUID tenantId, UUID caseId, Pageable pageable);

    /**
     * Returns metadata for a single document.
     *
     * @param tenantId   owning tenant
     * @param documentId MongoDB document id
     * @return document DTO
     */
    DocumentDto findById(UUID tenantId, String documentId);

    /**
     * Streams the content of the latest version of a document.
     *
     * @param tenantId   owning tenant
     * @param documentId MongoDB document id
     * @return raw input stream — caller must close
     */
    InputStream downloadLatest(UUID tenantId, String documentId);

    /**
     * Streams the content of a specific version of a document.
     *
     * @param tenantId      owning tenant
     * @param documentId    MongoDB document id
     * @param versionNumber 1-based version number
     * @return raw input stream — caller must close
     */
    InputStream downloadVersion(UUID tenantId, String documentId, int versionNumber);

    /**
     * Soft-deletes a document (sets {@code deleted = true}).
     *
     * @param tenantId   owning tenant
     * @param documentId MongoDB document id
     */
    void deleteDocument(UUID tenantId, String documentId);
}

