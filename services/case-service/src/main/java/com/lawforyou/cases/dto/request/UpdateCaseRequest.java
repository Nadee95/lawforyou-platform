package com.lawforyou.cases.dto.request;

import com.lawforyou.cases.entity.CaseType;

public record UpdateCaseRequest(
        String title,
        String description,
        CaseType caseType
) {}

