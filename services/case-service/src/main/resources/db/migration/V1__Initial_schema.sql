-- =============================================================================
-- V1__Initial_schema.sql
-- case-service initial schema
-- =============================================================================

-- cases -----------------------------------------------------------------------
CREATE TABLE cases (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    case_type   VARCHAR(50)  NOT NULL,                       -- CIVIL, CRIMINAL, FAMILY, CORPORATE, OTHER
    status      VARCHAR(50)  NOT NULL DEFAULT 'OPEN',        -- OPEN, IN_PROGRESS, CLOSED, ARCHIVED
    client_id   UUID         NOT NULL,                       -- references user-service user UUID
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

CREATE INDEX idx_cases_tenant_id        ON cases(tenant_id);
CREATE INDEX idx_cases_tenant_status    ON cases(tenant_id, status);
CREATE INDEX idx_cases_tenant_type      ON cases(tenant_id, case_type);
CREATE INDEX idx_cases_client_id        ON cases(client_id);

-- case_assignments ------------------------------------------------------------
CREATE TABLE case_assignments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    case_id     UUID        NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    lawyer_id   UUID        NOT NULL,                        -- references user-service user UUID
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by VARCHAR(100),
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_assignments_case_id   ON case_assignments(case_id);
CREATE INDEX idx_assignments_lawyer_id ON case_assignments(lawyer_id);
-- Only one active assignment per case at a time
CREATE UNIQUE INDEX uq_active_assignment ON case_assignments(case_id) WHERE active = TRUE;

-- outbox_events ---------------------------------------------------------------
CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(200) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status     ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_aggregate  ON outbox_events(aggregate_type, aggregate_id);

