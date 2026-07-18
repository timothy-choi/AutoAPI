CREATE TABLE traffic_split_policies (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    selection_key VARCHAR(32) NOT NULL,
    selection_key_name VARCHAR(255) NULL,
    fallback_mode VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_traffic_split_policies_api_name UNIQUE (api_id, name),
    CONSTRAINT chk_traffic_split_policies_selection_key CHECK (
        selection_key IN ('REQUEST_ID', 'API_KEY_ID', 'HEADER', 'COOKIE')
    ),
    CONSTRAINT chk_traffic_split_policies_fallback_mode CHECK (
        fallback_mode IN ('STRICT', 'FALLBACK_TO_ANY_HEALTHY_SPLIT', 'FALLBACK_TO_PRIMARY')
    ),
    CONSTRAINT chk_traffic_split_policies_selection_key_name CHECK (
        (selection_key IN ('REQUEST_ID', 'API_KEY_ID') AND selection_key_name IS NULL)
        OR (selection_key IN ('HEADER', 'COOKIE') AND selection_key_name IS NOT NULL)
    )
);

CREATE INDEX idx_traffic_split_policies_api_id ON traffic_split_policies (api_id);

CREATE TABLE traffic_split_destinations (
    id UUID PRIMARY KEY,
    traffic_split_policy_id UUID NOT NULL REFERENCES traffic_split_policies (id),
    upstream_pool_id UUID NOT NULL REFERENCES upstream_pools (id),
    name VARCHAR(255) NOT NULL,
    weight INTEGER NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_traffic_split_destinations_policy_pool UNIQUE (traffic_split_policy_id, upstream_pool_id),
    CONSTRAINT uq_traffic_split_destinations_policy_name UNIQUE (traffic_split_policy_id, name),
    CONSTRAINT chk_traffic_split_destinations_weight CHECK (weight >= 0 AND weight <= 10000),
    CONSTRAINT chk_traffic_split_destinations_priority CHECK (priority >= 0)
);

CREATE INDEX idx_traffic_split_destinations_policy_id
    ON traffic_split_destinations (traffic_split_policy_id);

ALTER TABLE routes
    ALTER COLUMN upstream_pool_id DROP NOT NULL;

ALTER TABLE route_policy_bindings
    ADD COLUMN traffic_split_policy_id UUID NULL REFERENCES traffic_split_policies (id);

CREATE INDEX idx_route_policy_bindings_traffic_split_policy_id
    ON route_policy_bindings (traffic_split_policy_id);
