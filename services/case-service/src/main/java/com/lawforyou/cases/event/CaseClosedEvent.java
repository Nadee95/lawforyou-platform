package com.lawforyou.cases.event;

import com.lawforyou.cases.entity.CaseStatus;
import java.time.Instant;
import java.util.UUID;

/** Published to {@code case-events} when a case is closed or archived. */
public record CaseClosedEvent(
        UUID       caseId,
        UUID       tenantId,
        CaseStatus finalStatus,
        Instant    occurredAt
) {}

