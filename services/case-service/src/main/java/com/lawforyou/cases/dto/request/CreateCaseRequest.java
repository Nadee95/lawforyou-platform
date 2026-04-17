package com.lawforyou.cases.dto.request;

import com.lawforyou.cases.entity.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCaseRequest(
        @NotBlank String title,
        String description,
        @NotNull CaseType caseType,
        @NotNull UUID clientId
) {}

