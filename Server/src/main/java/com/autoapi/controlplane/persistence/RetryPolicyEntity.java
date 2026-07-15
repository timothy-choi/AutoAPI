package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("retry_policies")
public record RetryPolicyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    String name,
    @Column("max_attempts") int maxAttempts,
    @Column("per_attempt_timeout_ms") int perAttemptTimeoutMs,
    @Column("retry_on_connect_failure") boolean retryOnConnectFailure,
    @Column("retry_on_connection_reset") boolean retryOnConnectionReset,
    @Column("retry_on_dns_failure") boolean retryOnDnsFailure,
    @Column("retry_on_response_timeout") boolean retryOnResponseTimeout,
    @Column("retryable_methods") String[] retryableMethods,
    @Column("require_idempotency_key_for_unsafe_methods")
        boolean requireIdempotencyKeyForUnsafeMethods,
    @Column("budget_percent") int budgetPercent,
    @Column("budget_min_retries_per_second") int budgetMinRetriesPerSecond,
    @Column("budget_window_seconds") int budgetWindowSeconds,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt)
    implements NewEntity {}
