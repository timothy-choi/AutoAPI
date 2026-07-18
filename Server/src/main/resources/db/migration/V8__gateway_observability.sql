CREATE TABLE gateway_instances (
    id UUID PRIMARY KEY,
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways (id),
    instance_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    software_version VARCHAR(64) NOT NULL,
    active_snapshot_version BIGINT NULL,
    active_snapshot_activated_at TIMESTAMPTZ NULL,
    route_count INTEGER NOT NULL DEFAULT 0,
    target_count INTEGER NOT NULL DEFAULT 0,
    uptime_seconds BIGINT NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_gateway_instances_instance UNIQUE (gateway_id, instance_id)
);

CREATE INDEX idx_gateway_instances_gateway_id ON gateway_instances (gateway_id);
CREATE INDEX idx_gateway_instances_last_seen_at ON gateway_instances (last_seen_at DESC);
CREATE INDEX idx_gateway_instances_status ON gateway_instances (status);
CREATE INDEX idx_gateway_instances_active_snapshot_version ON gateway_instances (active_snapshot_version);

CREATE TABLE request_summaries (
    id UUID PRIMARY KEY,
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways (id),
    instance_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64) NULL,
    api_id UUID NULL,
    route_id VARCHAR(128) NULL,
    method VARCHAR(16) NOT NULL,
    status INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_request_summaries_created_at ON request_summaries (created_at DESC);
CREATE INDEX idx_request_summaries_gateway_id ON request_summaries (gateway_id, created_at DESC);
CREATE INDEX idx_request_summaries_request_id ON request_summaries (request_id);
