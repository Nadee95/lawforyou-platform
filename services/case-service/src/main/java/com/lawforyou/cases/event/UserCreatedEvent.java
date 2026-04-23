package com.lawforyou.cases.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Mirrors {@code com.lawforyou.user.event.UserCreatedEvent} from user-service.
 *
 * <p>Published to the {@code user-events} Kafka topic when a user registers.
 * Consumed by case-service to maintain a local user reference (e.g. for client lookups).</p>
 */
public record UserCreatedEvent(
        UUID        userId,
        UUID        tenantId,
        String      username,
        String      email,
        String      firstName,
        String      lastName,
        Set<String> roles,
        Instant     occurredAt
) {}

