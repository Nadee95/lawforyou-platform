-- Add per-event Kafka topic routing to outbox_events.
-- Existing rows all belong on 'case-events' — backfill then enforce NOT NULL.

ALTER TABLE outbox_events
    ADD COLUMN topic VARCHAR(200);

UPDATE outbox_events
    SET topic = 'case-events'
    WHERE topic IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN topic SET NOT NULL;

CREATE INDEX idx_outbox_topic ON outbox_events(topic, status, created_at)
    WHERE status = 'PENDING';

