CREATE TABLE outbox_events (
                               id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id  VARCHAR(100) NOT NULL,
                               event_type    VARCHAR(200) NOT NULL,
                               payload       TEXT NOT NULL,
                               status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               retry_count   INT NOT NULL DEFAULT 0,
                               created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               processed_at  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at) WHERE status = 'PENDING';
