package com.lawforyou.cases.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignLawyerRequest(@NotNull UUID lawyerId) {}

