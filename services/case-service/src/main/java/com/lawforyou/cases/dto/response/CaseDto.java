package com.lawforyou.cases.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseDto(
        UUID       id,
        UUID       tenantId,
        String     title,
        String     description,
        CaseType   caseType,
        CaseStatus status,
        UUID       clientId,
        UUID       activeLawyerId,   // null if unassigned
        Instant    createdAt,
        Instant    updatedAt,
        String     createdBy,
        String     updatedBy
) {}

