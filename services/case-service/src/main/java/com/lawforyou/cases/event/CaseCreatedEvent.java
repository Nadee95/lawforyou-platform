package com.lawforyou.cases.event;

import com.lawforyou.cases.entity.CaseType;
import java.time.Instant;
import java.util.UUID;

/** Published to {@code case-events} when a new case is created. */
public record CaseCreatedEvent(
        UUID     caseId,
        UUID     tenantId,
        UUID     clientId,
        String   title,
        CaseType caseType,
        Instant  occurredAt
) {}

