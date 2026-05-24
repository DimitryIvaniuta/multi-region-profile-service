CREATE TABLE IF NOT EXISTS profile_read_model (
    profile_id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    phone VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    source_region VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    replicated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_profile_read_model_email ON profile_read_model (email);
CREATE INDEX IF NOT EXISTS idx_profile_read_model_event_time ON profile_read_model (event_time DESC);

CREATE TABLE IF NOT EXISTS consumed_profile_events (
    event_id UUID PRIMARY KEY,
    profile_id UUID NOT NULL,
    source_region VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_consumed_profile_events_profile_id
    ON consumed_profile_events (profile_id);
