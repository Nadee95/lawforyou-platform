package com.lawforyou.cases.dto.request;

import com.lawforyou.cases.entity.CaseStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeCaseStatusRequest(@NotNull CaseStatus status) {}

