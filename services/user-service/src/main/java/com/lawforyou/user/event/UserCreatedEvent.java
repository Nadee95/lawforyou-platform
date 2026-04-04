package com.lawforyou.user.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Event published to Kafka topic {@code user-events} when a new user registers.
 * Consumed by case-service, notification-service, etc.
 */
public record UserCreatedEvent(
        UUID    userId,
        UUID    tenantId,
        String  username,
        String  email,
        String  firstName,
        String  lastName,
        Set<String> roles,
        Instant occurredAt
) {}

