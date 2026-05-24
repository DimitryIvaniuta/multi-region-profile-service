ALTER TABLE profile_outbox
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_profile_outbox_claimable
    ON profile_outbox (status, next_attempt_at, locked_at, attempts, created_at);

CREATE TABLE IF NOT EXISTS profile_projection_watermark (
    topic_name VARCHAR(255) NOT NULL,
    partition_id INTEGER NOT NULL,
    current_offset BIGINT NOT NULL,
    latest_event_time TIMESTAMPTZ NOT NULL,
    last_consumed_at TIMESTAMPTZ NOT NULL,
    region VARCHAR(64) NOT NULL,
    PRIMARY KEY (topic_name, partition_id)
);

CREATE INDEX IF NOT EXISTS idx_profile_projection_watermark_latest_event_time
    ON profile_projection_watermark (latest_event_time DESC);

CREATE TABLE IF NOT EXISTS invalid_profile_events (
    id BIGSERIAL PRIMARY KEY,
    topic_name VARCHAR(255) NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    record_key VARCHAR(512),
    payload TEXT NOT NULL,
    error TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    region VARCHAR(64) NOT NULL,
    CONSTRAINT uq_invalid_profile_event_position UNIQUE (topic_name, partition_id, record_offset)
);

CREATE INDEX IF NOT EXISTS idx_invalid_profile_events_occurred_at
    ON invalid_profile_events (occurred_at DESC);
