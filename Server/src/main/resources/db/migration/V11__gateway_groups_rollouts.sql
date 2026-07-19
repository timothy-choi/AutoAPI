-- Phase 12: gateway groups and progressive runtime rollouts

ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS region VARCHAR(64),
    ADD COLUMN IF NOT EXISTS zone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS environment VARCHAR(64),
    ADD COLUMN IF NOT EXISTS labels JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS admin_labels JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS runtime_schema_version INTEGER,
    ADD COLUMN IF NOT EXISTS capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS gateway_software_version VARCHAR(64);

CREATE TABLE gateway_groups (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    api_id UUID NOT NULL REFERENCES apis(id),
    name VARCHAR(128) NOT NULL,
    description TEXT,
    region VARCHAR(64),
    zone VARCHAR(64),
    environment VARCHAR(64),
    selector JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    desired_config_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT gateway_groups_desired_version_fk
        FOREIGN KEY (api_id, desired_config_version)
        REFERENCES config_versions (api_id, version)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE UNIQUE INDEX gateway_groups_project_name_active
    ON gateway_groups (project_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX gateway_groups_project_id_idx ON gateway_groups (project_id);
CREATE INDEX gateway_groups_api_id_idx ON gateway_groups (api_id);

CREATE TABLE gateway_group_memberships (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    gateway_group_id UUID NOT NULL REFERENCES gateway_groups(id),
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways(id),
    membership_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT gateway_group_memberships_type_check
        CHECK (membership_type IN ('EXPLICIT_INCLUDE', 'EXPLICIT_EXCLUDE', 'SELECTOR')),
    CONSTRAINT gateway_group_memberships_unique_gateway
        UNIQUE (project_id, gateway_id)
);

CREATE INDEX gateway_group_memberships_group_idx
    ON gateway_group_memberships (gateway_group_id);

CREATE TABLE runtime_rollouts (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    gateway_group_id UUID NOT NULL REFERENCES gateway_groups(id),
    api_id UUID NOT NULL REFERENCES apis(id),
    source_version BIGINT NOT NULL,
    target_version BIGINT NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    progression_mode VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage_index INTEGER NOT NULL DEFAULT -1,
    membership_mode VARCHAR(32) NOT NULL DEFAULT 'SNAPSHOT_AT_START',
    auto_rollback_on_failure BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_behavior VARCHAR(32) NOT NULL DEFAULT 'KEEP_CURRENT_ASSIGNMENTS',
    correlation_id UUID NOT NULL,
    created_by_actor_type VARCHAR(32),
    created_by_actor_id VARCHAR(128),
    started_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    pause_accumulated_ms BIGINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_summary VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT runtime_rollouts_strategy_check
        CHECK (strategy IN ('ALL_AT_ONCE', 'PROGRESSIVE_PERCENTAGE')),
    CONSTRAINT runtime_rollouts_progression_check
        CHECK (progression_mode IN ('MANUAL', 'AUTOMATIC')),
    CONSTRAINT runtime_rollouts_status_check
        CHECK (status IN (
            'DRAFT', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'CANCELLED',
            'ROLLING_BACK', 'ROLLED_BACK', 'ROLLBACK_FAILED')),
    CONSTRAINT runtime_rollouts_membership_mode_check
        CHECK (membership_mode IN ('SNAPSHOT_AT_START')),
    CONSTRAINT runtime_rollouts_cancel_behavior_check
        CHECK (cancel_behavior IN ('KEEP_CURRENT_ASSIGNMENTS', 'ROLL_BACK_ASSIGNED_GATEWAYS')),
    CONSTRAINT runtime_rollouts_source_fk
        FOREIGN KEY (api_id, source_version) REFERENCES config_versions (api_id, version),
    CONSTRAINT runtime_rollouts_target_fk
        FOREIGN KEY (api_id, target_version) REFERENCES config_versions (api_id, version)
);

CREATE UNIQUE INDEX runtime_rollouts_one_active_per_group
    ON runtime_rollouts (gateway_group_id)
    WHERE status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK');

CREATE INDEX runtime_rollouts_active_poll_idx
    ON runtime_rollouts (status, updated_at)
    WHERE status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK');

CREATE INDEX runtime_rollouts_project_idx ON runtime_rollouts (project_id, created_at DESC);

CREATE TABLE runtime_rollout_stages (
    id UUID PRIMARY KEY,
    rollout_id UUID NOT NULL REFERENCES runtime_rollouts(id) ON DELETE CASCADE,
    stage_index INTEGER NOT NULL,
    percentage INTEGER NOT NULL,
    minimum_gateway_count INTEGER NOT NULL DEFAULT 1,
    maximum_gateway_count INTEGER,
    required_converged_percentage INTEGER NOT NULL DEFAULT 100,
    maximum_failed_gateways INTEGER NOT NULL DEFAULT 0,
    maximum_timed_out_gateways INTEGER NOT NULL DEFAULT 0,
    required_online_percentage INTEGER NOT NULL DEFAULT 0,
    observation_duration_ms BIGINT NOT NULL,
    stage_timeout_ms BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    observation_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_summary VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT runtime_rollout_stages_unique_index UNIQUE (rollout_id, stage_index),
    CONSTRAINT runtime_rollout_stages_percentage_check
        CHECK (percentage > 0 AND percentage <= 100),
    CONSTRAINT runtime_rollout_stages_status_check
        CHECK (status IN (
            'PENDING', 'RUNNING', 'OBSERVING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED'))
);

CREATE INDEX runtime_rollout_stages_timeout_idx
    ON runtime_rollout_stages (status, started_at)
    WHERE status = 'RUNNING';

CREATE TABLE runtime_rollout_gateways (
    rollout_id UUID NOT NULL REFERENCES runtime_rollouts(id) ON DELETE CASCADE,
    gateway_id VARCHAR(128) NOT NULL REFERENCES gateways(id),
    cohort_rank BIGINT NOT NULL,
    assigned_stage_index INTEGER,
    previous_desired_version BIGINT,
    target_desired_version BIGINT,
    assignment_generation BIGINT NOT NULL DEFAULT 0,
    rollback_generation BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    eligible BOOLEAN NOT NULL DEFAULT TRUE,
    exclusion_reason VARCHAR(256),
    assigned_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    timed_out_at TIMESTAMPTZ,
    rolled_back_at TIMESTAMPTZ,
    last_reported_version BIGINT,
    last_error_code VARCHAR(64),
    last_error_summary VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (rollout_id, gateway_id),
    CONSTRAINT runtime_rollout_gateways_status_check
        CHECK (status IN (
            'PENDING', 'ASSIGNED', 'DELIVERED', 'ACKNOWLEDGED', 'ACTIVATED', 'FAILED',
            'TIMED_OUT', 'EXCLUDED', 'ROLLBACK_ASSIGNED', 'ROLLED_BACK', 'ROLLBACK_FAILED'))
);

CREATE INDEX runtime_rollout_gateways_status_idx
    ON runtime_rollout_gateways (rollout_id, status);

CREATE INDEX runtime_rollout_gateways_gateway_idx
    ON runtime_rollout_gateways (gateway_id, rollout_id);
