package com.lawforyou.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to Kafka topic {@code user-events} when a user's profile changes.
 */
public record UserUpdatedEvent(
        UUID    userId,
        UUID    tenantId,
        String  username,
        String  email,
        boolean active,
        Instant occurredAt
) {}

