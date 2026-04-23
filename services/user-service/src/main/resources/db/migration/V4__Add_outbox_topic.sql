-- Add routing topic to outbox_events so the OutboxRelay knows which Kafka
-- topic to publish each event to, rather than using a single hardcoded topic.
--
-- Existing rows (UserCreatedEvent / UserUpdatedEvent) all belong on 'user-events',
-- so we backfill with that value and then add the NOT NULL constraint.

ALTER TABLE outbox_events
    ADD COLUMN topic VARCHAR(200);

UPDATE outbox_events
    SET topic = 'user-events'
    WHERE topic IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN topic SET NOT NULL;

-- Index for fast relay queries scoped by topic (useful when topics grow)
CREATE INDEX idx_outbox_topic ON outbox_events(topic, status, created_at)
    WHERE status = 'PENDING';

