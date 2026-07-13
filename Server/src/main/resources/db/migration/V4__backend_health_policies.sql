CREATE TABLE backend_health_policies (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    consecutive_failure_threshold INTEGER NOT NULL,
    ejection_duration_seconds INTEGER NOT NULL,
    max_ejection_percent INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_backend_health_policies_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_backend_health_policies_threshold CHECK (
        consecutive_failure_threshold >= 1 AND consecutive_failure_threshold <= 100
    ),
    CONSTRAINT chk_backend_health_policies_ejection_duration CHECK (
        ejection_duration_seconds >= 1 AND ejection_duration_seconds <= 3600
    ),
    CONSTRAINT chk_backend_health_policies_max_ejection CHECK (
        max_ejection_percent >= 0 AND max_ejection_percent <= 100
    )
);

CREATE INDEX idx_backend_health_policies_api_id ON backend_health_policies (api_id);

ALTER TABLE upstream_pools
    ADD COLUMN backend_health_policy_id UUID NULL
        REFERENCES backend_health_policies (id);

CREATE INDEX idx_upstream_pools_backend_health_policy_id
    ON upstream_pools (backend_health_policy_id);
