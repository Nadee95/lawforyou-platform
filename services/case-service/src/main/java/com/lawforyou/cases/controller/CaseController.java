package com.lawforyou.cases.controller;

import com.lawforyou.cases.dto.request.*;
import com.lawforyou.cases.dto.response.CaseDto;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import com.lawforyou.cases.service.CaseService;
import com.nadeex.spring.common.response.ApiResponse;
import com.nadeex.spring.common.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CaseDto> createCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal String userId) {

        CaseDto dto = caseService.createCase(request, tenantId, userId);
        return ApiResponse.success(dto, "Case created successfully");
    }

    @GetMapping
    public PagedResponse<CaseDto> findAll(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) CaseType   caseType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return caseService.findAll(tenantId, status, caseType,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CaseDto> findById(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id) {

        return ApiResponse.success(caseService.findById(id, tenantId));
    }

    @PutMapping("/{id}")
    public ApiResponse<CaseDto> updateCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseRequest request,
            @AuthenticationPrincipal String userId) {

        return ApiResponse.success(caseService.updateCase(id, tenantId, request, userId));
    }

    @PatchMapping("/{id}/assign")
    public ApiResponse<CaseDto> assignLawyer(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AssignLawyerRequest request,
            @AuthenticationPrincipal String userId) {

        return ApiResponse.success(caseService.assignLawyer(id, tenantId, request, userId));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<CaseDto> changeStatus(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeCaseStatusRequest request) {

        return ApiResponse.success(caseService.changeStatus(id, tenantId, request));
    }
}

