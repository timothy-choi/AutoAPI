CREATE TABLE retry_policies (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    max_attempts INTEGER NOT NULL,
    per_attempt_timeout_ms INTEGER NOT NULL,
    retry_on_connect_failure BOOLEAN NOT NULL,
    retry_on_connection_reset BOOLEAN NOT NULL,
    retry_on_dns_failure BOOLEAN NOT NULL,
    retry_on_response_timeout BOOLEAN NOT NULL,
    retryable_methods TEXT[] NOT NULL,
    require_idempotency_key_for_unsafe_methods BOOLEAN NOT NULL,
    budget_percent INTEGER NOT NULL,
    budget_min_retries_per_second INTEGER NOT NULL,
    budget_window_seconds INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_retry_policies_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_retry_policies_max_attempts CHECK (
        max_attempts >= 1 AND max_attempts <= 5
    ),
    CONSTRAINT chk_retry_policies_timeout CHECK (
        per_attempt_timeout_ms >= 50 AND per_attempt_timeout_ms <= 30000
    ),
    CONSTRAINT chk_retry_policies_budget_percent CHECK (
        budget_percent >= 0 AND budget_percent <= 100
    ),
    CONSTRAINT chk_retry_policies_budget_min CHECK (
        budget_min_retries_per_second >= 0 AND budget_min_retries_per_second <= 10000
    ),
    CONSTRAINT chk_retry_policies_budget_window CHECK (
        budget_window_seconds >= 1 AND budget_window_seconds <= 300
    )
);

CREATE INDEX idx_retry_policies_api_id ON retry_policies (api_id);

ALTER TABLE route_policy_bindings
    ADD COLUMN retry_policy_id UUID NULL REFERENCES retry_policies (id);

CREATE INDEX idx_route_policy_bindings_retry_policy_id
    ON route_policy_bindings (retry_policy_id);
