CREATE TABLE circuit_breaker_policies (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    failure_threshold INTEGER NOT NULL,
    rolling_window_seconds INTEGER NOT NULL,
    open_duration_seconds INTEGER NOT NULL,
    half_open_max_requests INTEGER NOT NULL,
    success_threshold INTEGER NOT NULL,
    predicate_count_http_5xx BOOLEAN NOT NULL DEFAULT true,
    predicate_count_connect_failure BOOLEAN NOT NULL DEFAULT true,
    predicate_count_connect_timeout BOOLEAN NOT NULL DEFAULT true,
    predicate_count_read_timeout BOOLEAN NOT NULL DEFAULT true,
    predicate_count_tls_failure BOOLEAN NOT NULL DEFAULT true,
    predicate_count_transport_exception BOOLEAN NOT NULL DEFAULT true,
    predicate_count_http_429 BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_circuit_breaker_policies_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_circuit_breaker_policies_failure_threshold CHECK (
        failure_threshold >= 1 AND failure_threshold <= 1000
    ),
    CONSTRAINT chk_circuit_breaker_policies_rolling_window CHECK (
        rolling_window_seconds >= 1 AND rolling_window_seconds <= 3600
    ),
    CONSTRAINT chk_circuit_breaker_policies_open_duration CHECK (
        open_duration_seconds >= 1 AND open_duration_seconds <= 3600
    ),
    CONSTRAINT chk_circuit_breaker_policies_half_open_max CHECK (
        half_open_max_requests >= 1 AND half_open_max_requests <= 100
    ),
    CONSTRAINT chk_circuit_breaker_policies_success_threshold CHECK (
        success_threshold >= 1 AND success_threshold <= 100
    )
);

CREATE INDEX idx_circuit_breaker_policies_api_id ON circuit_breaker_policies (api_id);

ALTER TABLE route_policy_bindings
    ADD COLUMN circuit_breaker_policy_id UUID NULL REFERENCES circuit_breaker_policies (id);

CREATE INDEX idx_route_policy_bindings_circuit_breaker_policy_id
    ON route_policy_bindings (circuit_breaker_policy_id);
