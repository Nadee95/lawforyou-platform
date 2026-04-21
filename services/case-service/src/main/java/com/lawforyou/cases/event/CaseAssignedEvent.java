package com.lawforyou.cases.event;

import java.time.Instant;
import java.util.UUID;

/** Published to {@code case-events} when a lawyer is assigned to a case. */
public record CaseAssignedEvent(
        UUID    caseId,
        UUID    tenantId,
        UUID    lawyerId,
        UUID    assignedBy,
        Instant occurredAt
) {}

