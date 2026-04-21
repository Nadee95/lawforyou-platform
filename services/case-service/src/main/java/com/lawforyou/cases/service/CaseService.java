package com.lawforyou.cases.service;

import com.lawforyou.cases.dto.request.*;
import com.lawforyou.cases.dto.response.CaseDto;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import com.nadeex.spring.common.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CaseService {

    CaseDto createCase(CreateCaseRequest request, UUID tenantId, String createdBy);

    PagedResponse<CaseDto> findAll(UUID tenantId, CaseStatus status, CaseType caseType, Pageable pageable);

    CaseDto findById(UUID caseId, UUID tenantId);

    CaseDto updateCase(UUID caseId, UUID tenantId, UpdateCaseRequest request, String updatedBy);

    CaseDto assignLawyer(UUID caseId, UUID tenantId, AssignLawyerRequest request, String assignedBy);

    CaseDto changeStatus(UUID caseId, UUID tenantId, ChangeCaseStatusRequest request);
}

