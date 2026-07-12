CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    key_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(128) NOT NULL,
    secret_digest BYTEA NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_api_keys_api_key_id UNIQUE (api_id, key_id),
    CONSTRAINT uq_api_keys_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_api_keys_secret_digest_length CHECK (octet_length(secret_digest) = 32)
);

CREATE INDEX idx_api_keys_api_id ON api_keys (api_id);
CREATE INDEX idx_api_keys_key_id ON api_keys (key_id);
CREATE INDEX idx_api_keys_expires_at ON api_keys (expires_at);

CREATE TABLE rate_limit_policies (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    limit_count INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    identity_source VARCHAR(64) NOT NULL,
    redis_failure_mode VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rate_limit_policies_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_rate_limit_policies_limit_count CHECK (limit_count > 0 AND limit_count <= 10000000),
    CONSTRAINT chk_rate_limit_policies_window_seconds CHECK (window_seconds > 0 AND window_seconds <= 86400),
    CONSTRAINT chk_rate_limit_policies_identity_source CHECK (identity_source = 'API_KEY'),
    CONSTRAINT chk_rate_limit_policies_redis_failure_mode CHECK (redis_failure_mode IN ('FAIL_OPEN', 'FAIL_CLOSED'))
);

CREATE INDEX idx_rate_limit_policies_api_id ON rate_limit_policies (api_id);

CREATE TABLE route_policy_bindings (
    route_id UUID PRIMARY KEY REFERENCES routes (id),
    authentication_required BOOLEAN NOT NULL DEFAULT FALSE,
    rate_limit_policy_id UUID NULL REFERENCES rate_limit_policies (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_route_policy_rate_limit_requires_auth CHECK (
        rate_limit_policy_id IS NULL OR authentication_required = TRUE
    )
);

CREATE INDEX idx_route_policy_bindings_policy_id ON route_policy_bindings (rate_limit_policy_id);
