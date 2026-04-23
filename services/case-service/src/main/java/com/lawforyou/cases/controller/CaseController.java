package com.lawforyou.cases.controller;

import com.lawforyou.cases.dto.request.*;
import com.lawforyou.cases.dto.response.CaseDto;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import com.lawforyou.cases.service.CaseService;
import com.nadeex.spring.common.response.ApiResponse;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.logging.annotation.Loggable;
import com.nadeex.spring.security.annotation.CurrentUser;
import com.nadeex.spring.security.userdetails.TenantAwareUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Cases", description = "Legal case management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Loggable
public class CaseController {

    private final CaseService caseService;

    @Operation(summary = "Create a new legal case")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CASE_CREATE')")
    public ApiResponse<CaseDto> createCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody CreateCaseRequest request,
            @CurrentUser TenantAwareUserDetails principal) {

        return ApiResponse.success(
                caseService.createCase(request, tenantId, principal.getUserId().toString()),
                "Case created successfully");
    }

    @Operation(summary = "List cases for the tenant (filterable by status / type)")
    @GetMapping
    @PreAuthorize("hasAuthority('CASE_READ')")
    public PagedResponse<CaseDto> findAll(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) CaseType   caseType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return caseService.findAll(tenantId, status, caseType,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Operation(summary = "Get a case by id")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public ApiResponse<CaseDto> findById(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id) {

        return ApiResponse.success(caseService.findById(id, tenantId));
    }

    @Operation(summary = "Update case title / description / type")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CASE_UPDATE')")
    public ApiResponse<CaseDto> updateCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseRequest request,
            @CurrentUser TenantAwareUserDetails principal) {

        return ApiResponse.success(
                caseService.updateCase(id, tenantId, request, principal.getUserId().toString()));
    }

    @Operation(summary = "Assign (or reassign) a lawyer to a case")
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('CASE_ASSIGN')")
    public ApiResponse<CaseDto> assignLawyer(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AssignLawyerRequest request,
            @CurrentUser TenantAwareUserDetails principal) {

        return ApiResponse.success(
                caseService.assignLawyer(id, tenantId, request, principal.getUserId().toString()));
    }

    @Operation(summary = "Change the status of a case")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CASE_UPDATE')")
    public ApiResponse<CaseDto> changeStatus(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeCaseStatusRequest request) {

        return ApiResponse.success(caseService.changeStatus(id, tenantId, request));
    }
}
