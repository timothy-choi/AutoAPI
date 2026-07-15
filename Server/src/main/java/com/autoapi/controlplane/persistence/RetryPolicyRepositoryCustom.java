package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RetryPolicyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public RetryPolicyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RetryPolicyEntity> update(
      UUID id,
      int maxAttempts,
      int perAttemptTimeoutMs,
      boolean retryOnConnectFailure,
      boolean retryOnConnectionReset,
      boolean retryOnDnsFailure,
      boolean retryOnResponseTimeout,
      String[] retryableMethods,
      boolean requireIdempotencyKeyForUnsafeMethods,
      int budgetPercent,
      int budgetMinRetriesPerSecond,
      int budgetWindowSeconds,
      boolean enabled,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE retry_policies
            SET max_attempts = :maxAttempts,
                per_attempt_timeout_ms = :perAttemptTimeoutMs,
                retry_on_connect_failure = :retryOnConnectFailure,
                retry_on_connection_reset = :retryOnConnectionReset,
                retry_on_dns_failure = :retryOnDnsFailure,
                retry_on_response_timeout = :retryOnResponseTimeout,
                retryable_methods = :retryableMethods,
                require_idempotency_key_for_unsafe_methods = :requireIdempotencyKeyForUnsafeMethods,
                budget_percent = :budgetPercent,
                budget_min_retries_per_second = :budgetMinRetriesPerSecond,
                budget_window_seconds = :budgetWindowSeconds,
                enabled = :enabled,
                updated_at = :updatedAt
            WHERE id = :id
            RETURNING id, api_id, name, max_attempts, per_attempt_timeout_ms,
                      retry_on_connect_failure, retry_on_connection_reset,
                      retry_on_dns_failure, retry_on_response_timeout,
                      retryable_methods, require_idempotency_key_for_unsafe_methods,
                      budget_percent, budget_min_retries_per_second, budget_window_seconds,
                      enabled, created_at, updated_at
            """)
        .bind("id", id)
        .bind("maxAttempts", maxAttempts)
        .bind("perAttemptTimeoutMs", perAttemptTimeoutMs)
        .bind("retryOnConnectFailure", retryOnConnectFailure)
        .bind("retryOnConnectionReset", retryOnConnectionReset)
        .bind("retryOnDnsFailure", retryOnDnsFailure)
        .bind("retryOnResponseTimeout", retryOnResponseTimeout)
        .bind("retryableMethods", retryableMethods)
        .bind("requireIdempotencyKeyForUnsafeMethods", requireIdempotencyKeyForUnsafeMethods)
        .bind("budgetPercent", budgetPercent)
        .bind("budgetMinRetriesPerSecond", budgetMinRetriesPerSecond)
        .bind("budgetWindowSeconds", budgetWindowSeconds)
        .bind("enabled", enabled)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private RetryPolicyEntity mapRow(io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new RetryPolicyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("max_attempts", Integer.class),
        row.get("per_attempt_timeout_ms", Integer.class),
        row.get("retry_on_connect_failure", Boolean.class),
        row.get("retry_on_connection_reset", Boolean.class),
        row.get("retry_on_dns_failure", Boolean.class),
        row.get("retry_on_response_timeout", Boolean.class),
        row.get("retryable_methods", String[].class),
        row.get("require_idempotency_key_for_unsafe_methods", Boolean.class),
        row.get("budget_percent", Integer.class),
        row.get("budget_min_retries_per_second", Integer.class),
        row.get("budget_window_seconds", Integer.class),
        row.get("enabled", Boolean.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
