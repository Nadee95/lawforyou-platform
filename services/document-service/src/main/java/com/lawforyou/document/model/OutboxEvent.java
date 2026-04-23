package com.lawforyou.document.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB-backed transactional outbox event.
 * Polled by {@link com.lawforyou.document.kafka.OutboxRelay} and published to Kafka.
 */
@Document(collection = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private String id;

    private String aggregateType;
    private String aggregateId;

    /** Fully-qualified class name of the event payload (used for deserialisation). */
    private String eventType;

    /** JSON-serialised event payload. */
    private String payload;

    @Builder.Default
    @Indexed
    private String status = "PENDING";

    @Builder.Default
    private int retryCount = 0;

    private Instant createdAt;
    private Instant processedAt;
}

