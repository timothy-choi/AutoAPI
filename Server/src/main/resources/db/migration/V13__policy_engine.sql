-- Phase 14: Centralized policy engine — bundles, revisions, assignments, overrides, cache metadata, audit.

CREATE TABLE policy_bundles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(128) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT policy_bundles_org_name_unique UNIQUE (organization_id, name)
);

CREATE INDEX policy_bundles_organization_id_idx ON policy_bundles (organization_id);

CREATE TABLE policy_bundle_revisions (
    id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES policy_bundles (id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    content_json JSONB NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_principal_type VARCHAR(32),
    created_by_principal_id UUID,
    CONSTRAINT policy_bundle_revisions_unique UNIQUE (bundle_id, revision_number),
    CONSTRAINT policy_bundle_revisions_number_positive CHECK (revision_number > 0)
);

CREATE INDEX policy_bundle_revisions_bundle_id_idx ON policy_bundle_revisions (bundle_id);

CREATE TABLE policy_bundle_assignments (
    id UUID PRIMARY KEY,
    bundle_id UUID NOT NULL REFERENCES policy_bundles (id),
    revision_number INT NOT NULL,
    scope_level VARCHAR(32) NOT NULL,
    organization_id UUID REFERENCES organizations (id),
    project_id UUID REFERENCES projects (id),
    gateway_group_id UUID REFERENCES gateway_groups (id),
    api_id UUID REFERENCES apis (id),
    route_id UUID REFERENCES routes (id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT policy_bundle_assignments_scope_level_valid CHECK (
        scope_level IN ('ORGANIZATION', 'PROJECT', 'GATEWAY_GROUP', 'API', 'ROUTE')
    )
);

CREATE UNIQUE INDEX policy_bundle_assignments_org_unique
    ON policy_bundle_assignments (organization_id)
    WHERE scope_level = 'ORGANIZATION' AND enabled = TRUE;

CREATE UNIQUE INDEX policy_bundle_assignments_project_unique
    ON policy_bundle_assignments (project_id, bundle_id)
    WHERE scope_level = 'PROJECT' AND enabled = TRUE;

CREATE UNIQUE INDEX policy_bundle_assignments_gateway_group_unique
    ON policy_bundle_assignments (gateway_group_id, bundle_id)
    WHERE scope_level = 'GATEWAY_GROUP' AND enabled = TRUE;

CREATE UNIQUE INDEX policy_bundle_assignments_api_unique
    ON policy_bundle_assignments (api_id, bundle_id)
    WHERE scope_level = 'API' AND enabled = TRUE;

CREATE UNIQUE INDEX policy_bundle_assignments_route_unique
    ON policy_bundle_assignments (route_id, bundle_id)
    WHERE scope_level = 'ROUTE' AND enabled = TRUE;

CREATE INDEX policy_bundle_assignments_bundle_id_idx ON policy_bundle_assignments (bundle_id);

CREATE TABLE policy_overrides (
    id UUID PRIMARY KEY,
    scope_level VARCHAR(32) NOT NULL,
    organization_id UUID REFERENCES organizations (id),
    project_id UUID REFERENCES projects (id),
    gateway_group_id UUID REFERENCES gateway_groups (id),
    api_id UUID REFERENCES apis (id),
    route_id UUID REFERENCES routes (id),
    policy_type VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    content_json JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT policy_overrides_mode_valid CHECK (
        mode IN ('INHERIT', 'OVERRIDE', 'MERGE', 'DISABLE', 'APPEND')
    ),
    CONSTRAINT policy_overrides_scope_level_valid CHECK (
        scope_level IN ('ORGANIZATION', 'PROJECT', 'GATEWAY_GROUP', 'API', 'ROUTE')
    )
);

CREATE INDEX policy_overrides_scope_idx ON policy_overrides (scope_level, api_id, route_id);

CREATE TABLE effective_policy_cache_metadata (
    scope_key VARCHAR(512) PRIMARY KEY,
    cache_generation BIGINT NOT NULL DEFAULT 0,
    content_hash VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE policy_audit_log (
    id UUID PRIMARY KEY,
    actor_principal_type VARCHAR(32),
    actor_principal_id UUID,
    action VARCHAR(64) NOT NULL,
    scope_level VARCHAR(32),
    scope_resource_id UUID,
    policy_type VARCHAR(64),
    bundle_id UUID,
    bundle_revision INT,
    previous_value_json JSONB,
    new_value_json JSONB,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX policy_audit_log_created_at_idx ON policy_audit_log (created_at DESC);

CREATE TABLE global_policy_cache_generation (
    id INT PRIMARY KEY DEFAULT 1,
    generation BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT global_policy_cache_generation_singleton CHECK (id = 1)
);

INSERT INTO global_policy_cache_generation (id, generation, updated_at)
VALUES (1, 0, NOW())
ON CONFLICT (id) DO NOTHING;
