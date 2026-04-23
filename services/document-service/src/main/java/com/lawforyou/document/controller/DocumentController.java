package com.lawforyou.document.controller;

import com.lawforyou.document.dto.request.UploadDocumentRequest;
import com.lawforyou.document.dto.response.DocumentDto;
import com.lawforyou.document.service.DocumentService;
import com.nadeex.spring.common.response.ApiResponse;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.security.userdetails.TenantAwareUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * REST controller for document upload, download, listing and deletion.
 *
 * <p>All endpoints require a valid JWT. Tenant ID is extracted from the
 * {@link TenantAwareUserDetails} principal; uploader ID comes from {@code principal.userId}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload, download and management")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads a new document.
     *
     * <p>Send as {@code multipart/form-data} with two parts:
     * <ul>
     *   <li>{@code file} — binary file</li>
     *   <li>{@code metadata} — JSON {@link UploadDocumentRequest}</li>
     * </ul>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a document")
    public ApiResponse<DocumentDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid UploadDocumentRequest request,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        DocumentDto dto = documentService.uploadDocument(
                principal.getTenantId(),
                principal.getUserId().toString(),
                request,
                file);
        return ApiResponse.success(dto, "Document uploaded successfully");
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping("/case/{caseId}")
    @Operation(summary = "List documents for a case")
    public PagedResponse<DocumentDto> listByCase(
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return documentService.listByCase(principal.getTenantId(), caseId, pageable);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @GetMapping("/{documentId}")
    @Operation(summary = "Get document metadata")
    public ApiResponse<DocumentDto> getById(
            @PathVariable String documentId,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        return ApiResponse.success(documentService.findById(principal.getTenantId(), documentId));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/{documentId}/download")
    @Operation(summary = "Download the latest version of a document")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String documentId,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        DocumentDto meta = documentService.findById(principal.getTenantId(), documentId);
        InputStream stream = documentService.downloadLatest(principal.getTenantId(), documentId);
        return buildDownloadResponse(stream, meta.getOriginalFilename(), meta.getContentType());
    }

    @GetMapping("/{documentId}/versions/{version}/download")
    @Operation(summary = "Download a specific version of a document")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable String documentId,
            @PathVariable int version,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        DocumentDto meta = documentService.findById(principal.getTenantId(), documentId);
        InputStream stream = documentService.downloadVersion(principal.getTenantId(), documentId, version);
        return buildDownloadResponse(stream,
                meta.getOriginalFilename() + ".v" + version, meta.getContentType());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a document")
    public void delete(
            @PathVariable String documentId,
            Authentication authentication) {

        TenantAwareUserDetails principal = (TenantAwareUserDetails) authentication.getPrincipal();
        documentService.deleteDocument(principal.getTenantId(), documentId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<InputStreamResource> buildDownloadResponse(
            InputStream stream, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(stream));
    }
}

