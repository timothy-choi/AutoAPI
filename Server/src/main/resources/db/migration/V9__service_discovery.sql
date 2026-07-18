ALTER TABLE apis
    ADD COLUMN discovery_membership_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE discovered_services (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    selection_strategy VARCHAR(32) NOT NULL DEFAULT 'ROUND_ROBIN',
    registration_mode VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    default_scheme VARCHAR(16) NOT NULL DEFAULT 'http',
    default_port INTEGER NOT NULL DEFAULT 8080,
    consistent_hash_key VARCHAR(32) NOT NULL DEFAULT 'REQUEST_ID',
    consistent_hash_key_name VARCHAR(255) NULL,
    membership_version BIGINT NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_discovered_services_project_name UNIQUE (project_id, name),
    CONSTRAINT chk_discovered_services_selection_strategy CHECK (
        selection_strategy IN ('ROUND_ROBIN', 'CONSISTENT_HASH')
    ),
    CONSTRAINT chk_discovered_services_registration_mode CHECK (
        registration_mode IN ('OPEN', 'CREDENTIAL_REQUIRED')
    ),
    CONSTRAINT chk_discovered_services_default_scheme CHECK (
        default_scheme IN ('http', 'https')
    ),
    CONSTRAINT chk_discovered_services_default_port CHECK (
        default_port >= 1 AND default_port <= 65535
    ),
    CONSTRAINT chk_discovered_services_consistent_hash_key CHECK (
        consistent_hash_key IN ('REQUEST_ID', 'API_KEY_ID', 'HEADER')
    ),
    CONSTRAINT chk_discovered_services_consistent_hash_key_name CHECK (
        (consistent_hash_key = 'REQUEST_ID' AND consistent_hash_key_name IS NULL)
        OR (consistent_hash_key = 'API_KEY_ID' AND consistent_hash_key_name IS NULL)
        OR (consistent_hash_key = 'HEADER' AND consistent_hash_key_name IS NOT NULL)
    )
);

CREATE INDEX idx_discovered_services_project_id ON discovered_services (project_id);

CREATE TABLE service_registration_credentials (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES discovered_services (id) ON DELETE CASCADE,
    credential_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    secret_digest BYTEA NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_service_registration_credentials_service_credential_id
        UNIQUE (service_id, credential_id),
    CONSTRAINT uq_service_registration_credentials_service_name
        UNIQUE (service_id, name)
);

CREATE INDEX idx_service_registration_credentials_service_id
    ON service_registration_credentials (service_id);

CREATE TABLE service_instances (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES discovered_services (id) ON DELETE CASCADE,
    instance_id VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    scheme VARCHAR(16) NOT NULL,
    zone VARCHAR(64) NULL,
    region VARCHAR(64) NULL,
    weight INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL,
    registration_epoch BIGINT NOT NULL DEFAULT 1,
    registered_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    deregistered_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_service_instances_service_instance_id UNIQUE (service_id, instance_id),
    CONSTRAINT chk_service_instances_port CHECK (port >= 1 AND port <= 65535),
    CONSTRAINT chk_service_instances_scheme CHECK (scheme IN ('http', 'https')),
    CONSTRAINT chk_service_instances_weight CHECK (weight >= 1 AND weight <= 10000),
    CONSTRAINT chk_service_instances_status CHECK (
        status IN ('STARTING', 'READY', 'DRAINING', 'UNHEALTHY', 'STALE', 'DEREGISTERED')
    )
);

CREATE INDEX idx_service_instances_service_id ON service_instances (service_id);
CREATE INDEX idx_service_instances_status_lease ON service_instances (status, lease_expires_at);
CREATE INDEX idx_service_instances_service_status ON service_instances (service_id, status);

ALTER TABLE routes
    ADD COLUMN discovered_service_id UUID NULL REFERENCES discovered_services (id);

CREATE INDEX idx_routes_discovered_service_id ON routes (discovered_service_id);

ALTER TABLE traffic_split_destinations
    ALTER COLUMN upstream_pool_id DROP NOT NULL;

ALTER TABLE traffic_split_destinations
    ADD COLUMN discovered_service_id UUID NULL REFERENCES discovered_services (id);

ALTER TABLE traffic_split_destinations
    DROP CONSTRAINT IF EXISTS uq_traffic_split_destinations_policy_pool;

ALTER TABLE traffic_split_destinations
    ADD CONSTRAINT chk_traffic_split_destinations_target CHECK (
        (upstream_pool_id IS NOT NULL AND discovered_service_id IS NULL)
        OR (upstream_pool_id IS NULL AND discovered_service_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_traffic_split_destinations_policy_pool
    ON traffic_split_destinations (traffic_split_policy_id, upstream_pool_id)
    WHERE upstream_pool_id IS NOT NULL;

CREATE UNIQUE INDEX uq_traffic_split_destinations_policy_service
    ON traffic_split_destinations (traffic_split_policy_id, discovered_service_id)
    WHERE discovered_service_id IS NOT NULL;

CREATE INDEX idx_traffic_split_destinations_discovered_service_id
    ON traffic_split_destinations (discovered_service_id);

ALTER TABLE routes
    ADD CONSTRAINT chk_routes_target_mode CHECK (
        (
            upstream_pool_id IS NOT NULL
            AND discovered_service_id IS NULL
        )
        OR (
            upstream_pool_id IS NULL
            AND discovered_service_id IS NOT NULL
        )
        OR (
            upstream_pool_id IS NULL
            AND discovered_service_id IS NULL
        )
    );
