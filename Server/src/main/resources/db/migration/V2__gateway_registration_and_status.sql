CREATE TABLE gateways (
    id VARCHAR(128) PRIMARY KEY,
    gateway_group VARCHAR(128) NOT NULL DEFAULT 'default',
    software_version VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    instance_address VARCHAR(512) NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_gateways_last_seen_at ON gateways (last_seen_at);
CREATE INDEX idx_gateways_gateway_group ON gateways (gateway_group);

CREATE TABLE gateway_api_status (
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways (id),
    api_id UUID NOT NULL REFERENCES apis (id),
    active_version BIGINT NULL,
    active_content_hash VARCHAR(128) NULL,
    last_attempted_version BIGINT NULL,
    last_attempted_content_hash VARCHAR(128) NULL,
    last_status VARCHAR(32) NOT NULL,
    last_error_code VARCHAR(128) NULL,
    last_diagnostic TEXT NULL,
    last_apply_duration_ms BIGINT NULL,
    last_reported_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (gateway_id, api_id)
);

CREATE INDEX idx_gateway_api_status_api_id ON gateway_api_status (api_id);
CREATE INDEX idx_gateway_api_status_last_status ON gateway_api_status (last_status);
CREATE INDEX idx_gateway_api_status_last_reported_at ON gateway_api_status (last_reported_at);

CREATE TABLE config_activation_events (
    id UUID PRIMARY KEY,
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways (id),
    api_id UUID NOT NULL REFERENCES apis (id),
    version BIGINT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    report_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(128) NULL,
    diagnostic TEXT NULL,
    apply_duration_ms BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_config_activation_events_report UNIQUE (gateway_id, report_id)
);

CREATE INDEX idx_config_activation_events_api_version ON config_activation_events (api_id, version);
CREATE INDEX idx_config_activation_events_gateway_id ON config_activation_events (gateway_id);
CREATE INDEX idx_config_activation_events_created_at ON config_activation_events (created_at DESC);
