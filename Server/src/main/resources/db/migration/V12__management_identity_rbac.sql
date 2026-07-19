CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT chk_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE management_users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    external_subject VARCHAR(512) NOT NULL,
    email VARCHAR(320) NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_authenticated_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_management_users_org_external_subject UNIQUE (organization_id, external_subject),
    CONSTRAINT chk_management_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED'))
);

CREATE TABLE service_accounts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NULL REFERENCES projects (id),
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    disabled_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_service_accounts_org_project_name UNIQUE (organization_id, project_id, name),
    CONSTRAINT chk_service_accounts_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE role_bindings (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NULL REFERENCES projects (id),
    principal_type VARCHAR(32) NOT NULL,
    principal_id UUID NOT NULL,
    role VARCHAR(64) NOT NULL,
    created_by_principal_type VARCHAR(32) NOT NULL,
    created_by_principal_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_role_bindings_principal_type CHECK (
        principal_type IN ('USER', 'SERVICE_ACCOUNT', 'BOOTSTRAP_ADMIN')
    ),
    CONSTRAINT chk_role_bindings_created_by_type CHECK (
        created_by_principal_type IN ('USER', 'SERVICE_ACCOUNT', 'BOOTSTRAP_ADMIN', 'SYSTEM')
    ),
    CONSTRAINT chk_role_bindings_scope CHECK (
        (project_id IS NULL) OR (project_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_role_bindings_active_org_scope
    ON role_bindings (organization_id, project_id, principal_type, principal_id, role)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_role_bindings_principal_active
    ON role_bindings (principal_type, principal_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_role_bindings_organization_active
    ON role_bindings (organization_id)
    WHERE revoked_at IS NULL;

CREATE TABLE management_access_credentials (
    id UUID PRIMARY KEY,
    public_token_id VARCHAR(64) NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    principal_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    secret_digest BYTEA NOT NULL,
    digest_key_version INTEGER NOT NULL DEFAULT 1,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    last_used_at TIMESTAMPTZ NULL,
    last_used_source VARCHAR(64) NULL,
    revoked_at TIMESTAMPTZ NULL,
    rotated_from_credential_id UUID NULL REFERENCES management_access_credentials (id),
    CONSTRAINT uq_management_access_credentials_public_token_id UNIQUE (public_token_id),
    CONSTRAINT chk_management_access_credentials_principal_type CHECK (
        principal_type IN ('USER', 'SERVICE_ACCOUNT', 'BOOTSTRAP_ADMIN')
    ),
    CONSTRAINT chk_management_access_credentials_status CHECK (
        status IN ('ACTIVE', 'REVOKED', 'EXPIRED', 'ROTATED')
    )
);

CREATE INDEX idx_management_access_credentials_principal
    ON management_access_credentials (principal_type, principal_id);

CREATE INDEX idx_management_access_credentials_expires_at
    ON management_access_credentials (expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE management_auth_state (
    id INTEGER PRIMARY KEY DEFAULT 1,
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    bootstrap_organization_id UUID NULL REFERENCES organizations (id),
    initialized_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_management_auth_state_singleton CHECK (id = 1)
);

INSERT INTO management_auth_state (id, initialized) VALUES (1, FALSE);

ALTER TABLE projects
    ADD COLUMN organization_id UUID NULL REFERENCES organizations (id);

-- Bootstrap organization for existing projects (slug updated after org insert in app if needed).
INSERT INTO organizations (id, name, slug, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Default Organization',
    'default',
    'ACTIVE',
    NOW(),
    NOW()
);

UPDATE projects
SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE projects
    ALTER COLUMN organization_id SET NOT NULL;

CREATE INDEX idx_projects_organization_id ON projects (organization_id);

-- Extend platform event actor types for management identities.
ALTER TABLE platform_events DROP CONSTRAINT chk_platform_events_actor_type;
ALTER TABLE platform_events ADD CONSTRAINT chk_platform_events_actor_type CHECK (
    actor_type IN (
        'USER',
        'API_CLIENT',
        'GATEWAY',
        'SERVICE_INSTANCE',
        'SYSTEM',
        'SCHEDULED_JOB',
        'SERVICE_ACCOUNT',
        'BOOTSTRAP_ADMIN'
    )
);
