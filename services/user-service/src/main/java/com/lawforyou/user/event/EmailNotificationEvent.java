package com.lawforyou.user.event;

import java.time.Instant;

/**
 * Kafka event published to the {@code notification-events} topic.
 *
 * <p>Consumed by {@code communication-service} (NestJS), which maps it to its
 * own {@code NotificationEventDto}. The JSON field names must match exactly.</p>
 *
 * <pre>
 * Topic   : notification-events
 * Channel : EMAIL
 * Schema  :
 * {
 *   "type"          : "EMAIL",
 *   "to"            : "user@example.com",
 *   "subject"       : "Welcome to LawForYou",
 *   "body"          : "...",
 *   "tenantId"      : "uuid-string",
 *   "correlationId" : "uuid-string",
 *   "occurredAt"    : "2026-04-23T10:00:00Z"
 * }
 * </pre>
 *
 * @param type          Always {@code "EMAIL"} for this event.
 * @param to            Recipient email address.
 * @param subject       Email subject line.
 * @param body          Plain-text or HTML email body.
 * @param tenantId      Tenant UUID (string) for multi-tenancy context.
 * @param correlationId Trace ID propagated from the originating request (nullable).
 * @param occurredAt    ISO-8601 UTC timestamp of the originating domain event.
 */
public record EmailNotificationEvent(
        String type,
        String to,
        String subject,
        String body,
        String tenantId,
        String correlationId,
        String occurredAt
) {
    /** Kafka topic this event is published to. */
    public static final String TOPIC = "notification-events";

    /**
     * Factory — builds a welcome-email notification for a newly registered user.
     *
     * @param toEmail       recipient email address
     * @param username      the new user's login name (used in greeting)
     * @param tenantId      tenant UUID as string
     * @param correlationId request correlation ID (may be null)
     * @return populated event ready for the outbox
     */
    public static EmailNotificationEvent welcome(String toEmail,
                                                 String username,
                                                 String tenantId,
                                                 String correlationId) {
        return new EmailNotificationEvent(
                "EMAIL",
                toEmail,
                "Welcome to LawForYou!",
                """
                Hi %s,

                Your LawForYou account has been created successfully.
                You can now log in and start using the platform.

                If you did not register for this account, please contact support immediately.

                — The LawForYou Team
                """.formatted(username),
                tenantId,
                correlationId,
                Instant.now().toString()
        );
    }
}

