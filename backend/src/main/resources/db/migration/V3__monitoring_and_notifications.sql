-- Disruption monitoring, journey alerts and email notifications (Phase 4).

-- One row per disruption we have observed. TfL supplies no ids (feasibility.md §1.8), so
-- identity_key says WHICH disruption it is and content_hash says whether its text changed.
-- A content change bumps revision; a disruption that disappears from a successful read is RESOLVED.
CREATE TABLE disruption_event (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    source               text        NOT NULL CHECK (source IN ('LINE_STATUS', 'STOP_POINT', 'LIFT')),
    identity_key         text        NOT NULL,
    content_hash         text        NOT NULL,
    revision             integer     NOT NULL DEFAULT 1 CHECK (revision >= 1),
    category             text,
    type                 text,
    severity_code        integer,
    severity_description text,
    title                text        NOT NULL,
    description          text        NOT NULL,
    valid_from           timestamptz,
    valid_to             timestamptz,
    first_observed_at    timestamptz NOT NULL,
    last_observed_at     timestamptz NOT NULL,
    resolved_at          timestamptz,
    status               text        NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

-- At most one ACTIVE event per identity; resolved history may repeat the key.
CREATE UNIQUE INDEX disruption_event_active_identity_uq
    ON disruption_event (source, identity_key) WHERE status = 'ACTIVE';
CREATE INDEX disruption_event_status_idx ON disruption_event (status, resolved_at);

CREATE TABLE disruption_event_line (
    event_id uuid NOT NULL REFERENCES disruption_event (id) ON DELETE CASCADE,
    line_id  text NOT NULL,
    PRIMARY KEY (event_id, line_id)
);

CREATE TABLE disruption_event_station (
    event_id       uuid NOT NULL REFERENCES disruption_event (id) ON DELETE CASCADE,
    tfl_station_id text NOT NULL,
    PRIMARY KEY (event_id, tfl_station_id)
);

-- The outcome of each scheduler cycle.
CREATE TABLE monitoring_run (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    status          text        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')),
    events_created  integer     NOT NULL DEFAULT 0,
    events_updated  integer     NOT NULL DEFAULT 0,
    events_resolved integer     NOT NULL DEFAULT 0,
    alerts_created  integer     NOT NULL DEFAULT 0,
    error_summary   text
);

CREATE INDEX monitoring_run_started_idx ON monitoring_run (started_at DESC);

-- title/message are copied at alert time so an alert keeps saying what the user was told,
-- even after the event's text changes.
CREATE TABLE journey_alert (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    saved_journey_id    uuid        NOT NULL REFERENCES saved_journey (id) ON DELETE CASCADE,
    disruption_event_id uuid        NOT NULL REFERENCES disruption_event (id) ON DELETE CASCADE,
    event_revision      integer     NOT NULL,
    alert_type          text        NOT NULL CHECK (alert_type IN ('NEW', 'UPDATED', 'RESOLVED')),
    title               text        NOT NULL,
    message             text        NOT NULL,
    status              text        NOT NULL DEFAULT 'UNREAD' CHECK (status IN ('UNREAD', 'READ')),
    created_at          timestamptz NOT NULL,
    read_at             timestamptz,
    -- The dedup guarantee: re-running a cycle, or two instances racing, cannot alert twice.
    CONSTRAINT journey_alert_dedup UNIQUE (saved_journey_id, disruption_event_id, alert_type, event_revision)
);

CREATE INDEX journey_alert_user_idx ON journey_alert (user_id, status, created_at DESC);
CREATE INDEX journey_alert_event_idx ON journey_alert (disruption_event_id);

-- Absent row = defaults (email off, monitoring on).
CREATE TABLE notification_preference (
    user_id            uuid        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    email_enabled      boolean     NOT NULL DEFAULT false,
    monitoring_enabled boolean     NOT NULL DEFAULT true,
    updated_at         timestamptz NOT NULL
);

-- An outbox: written in the same transaction as the alert, sent afterwards, retried with backoff.
CREATE TABLE notification_delivery (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_alert_id uuid        NOT NULL REFERENCES journey_alert (id) ON DELETE CASCADE,
    channel          text        NOT NULL CHECK (channel IN ('EMAIL')),
    delivery_status  text        NOT NULL CHECK (delivery_status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    attempt_count    integer     NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz NOT NULL,
    last_attempt_at  timestamptz,
    sent_at          timestamptz,
    failure_reason   text,
    CONSTRAINT notification_delivery_once UNIQUE (journey_alert_id, channel)
);

CREATE INDEX notification_delivery_due_idx ON notification_delivery (delivery_status, next_attempt_at);
