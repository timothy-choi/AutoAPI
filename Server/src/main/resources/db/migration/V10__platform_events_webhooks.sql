CREATE SEQUENCE platform_event_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE platform_events (
    id UUID PRIMARY KEY,
    sequence BIGINT NOT NULL DEFAULT nextval('platform_event_sequence'),
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    project_id UUID NULL REFERENCES projects (id),
    api_id UUID NULL REFERENCES apis (id),
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    source VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NULL,
    causation_id UUID NULL REFERENCES platform_events (id),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    webhook_dispatch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_platform_events_sequence UNIQUE (sequence),
    CONSTRAINT chk_platform_events_actor_type CHECK (
        actor_type IN ('USER', 'API_CLIENT', 'GATEWAY', 'SERVICE_INSTANCE', 'SYSTEM', 'SCHEDULED_JOB')
    ),
    CONSTRAINT chk_platform_events_dispatch_status CHECK (
        webhook_dispatch_status IN ('PENDING', 'DISPATCHED', 'SKIPPED')
    )
);

CREATE INDEX idx_platform_events_project_sequence ON platform_events (project_id, sequence);
CREATE INDEX idx_platform_events_api_id ON platform_events (api_id);
CREATE INDEX idx_platform_events_event_type ON platform_events (event_type);
CREATE INDEX idx_platform_events_correlation_id ON platform_events (correlation_id);
CREATE INDEX idx_platform_events_occurred_at ON platform_events (occurred_at);
CREATE INDEX idx_platform_events_dispatch_pending
    ON platform_events (webhook_dispatch_status, sequence)
    WHERE webhook_dispatch_status = 'PENDING';

CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    event_filters JSONB NOT NULL DEFAULT '[]'::jsonb,
    resource_filters JSONB NOT NULL DEFAULT '[]'::jsonb,
    encrypted_secret BYTEA NOT NULL,
    secret_version INTEGER NOT NULL DEFAULT 1,
    max_attempts INTEGER NOT NULL DEFAULT 8,
    initial_backoff_seconds INTEGER NOT NULL DEFAULT 1,
    max_backoff_seconds INTEGER NOT NULL DEFAULT 300,
    timeout_ms INTEGER NOT NULL DEFAULT 5000,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    disabled_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_webhook_subscriptions_project_name UNIQUE (project_id, name),
    CONSTRAINT chk_webhook_subscriptions_max_attempts CHECK (max_attempts >= 1 AND max_attempts <= 20),
    CONSTRAINT chk_webhook_subscriptions_timeout CHECK (timeout_ms >= 1000 AND timeout_ms <= 60000)
);

CREATE INDEX idx_webhook_subscriptions_project_id ON webhook_subscriptions (project_id);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions (id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES platform_events (id),
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    dead_lettered_at TIMESTAMPTZ NULL,
    last_status_code INTEGER NULL,
    last_error_type VARCHAR(64) NULL,
    last_error_summary VARCHAR(512) NULL,
    secret_version INTEGER NOT NULL,
    replay_of_delivery_id UUID NULL REFERENCES webhook_deliveries (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_webhook_deliveries_subscription_event UNIQUE (subscription_id, event_id),
    CONSTRAINT chk_webhook_deliveries_status CHECK (
        status IN ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'RETRY_SCHEDULED', 'DEAD_LETTERED', 'CANCELLED')
    )
);

CREATE INDEX idx_webhook_deliveries_subscription_id ON webhook_deliveries (subscription_id);
CREATE INDEX idx_webhook_deliveries_event_id ON webhook_deliveries (event_id);
CREATE INDEX idx_webhook_deliveries_worker
    ON webhook_deliveries (status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'IN_PROGRESS');

CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhook_deliveries (id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    duration_ms INTEGER NULL,
    status_code INTEGER NULL,
    result VARCHAR(32) NOT NULL,
    error_type VARCHAR(64) NULL,
    response_body_preview VARCHAR(1024) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_webhook_delivery_attempts_result CHECK (
        result IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE', 'TIMEOUT')
    )
);

CREATE INDEX idx_webhook_delivery_attempts_delivery_id ON webhook_delivery_attempts (delivery_id);
