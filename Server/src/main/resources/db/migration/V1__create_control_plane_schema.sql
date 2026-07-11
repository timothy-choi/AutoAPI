CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_projects_name UNIQUE (name)
);

CREATE TABLE apis (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id),
    name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    base_path VARCHAR(512) NOT NULL DEFAULT '/',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    desired_config_version BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_apis_project_name UNIQUE (project_id, name)
);

CREATE TABLE upstream_pools (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    load_balancing VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_upstream_pools_api_name UNIQUE (api_id, name)
);

CREATE TABLE upstream_targets (
    id UUID PRIMARY KEY,
    upstream_pool_id UUID NOT NULL REFERENCES upstream_pools (id),
    url VARCHAR(2048) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    weight INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_upstream_targets_pool_url UNIQUE (upstream_pool_id, url),
    CONSTRAINT chk_upstream_targets_weight CHECK (weight > 0)
);

CREATE TABLE routes (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    path_prefix VARCHAR(512) NOT NULL,
    methods TEXT[] NOT NULL,
    upstream_pool_id UUID NOT NULL REFERENCES upstream_pools (id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_routes_api_name UNIQUE (api_id, name)
);

CREATE TABLE config_versions (
    id UUID PRIMARY KEY,
    api_id UUID NOT NULL REFERENCES apis (id),
    version BIGINT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    config_snapshot JSONB NOT NULL,
    message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_config_versions_api_version UNIQUE (api_id, version),
    CONSTRAINT uq_config_versions_api_hash UNIQUE (api_id, content_hash)
);

CREATE INDEX idx_config_versions_api_version_desc ON config_versions (api_id, version DESC);
