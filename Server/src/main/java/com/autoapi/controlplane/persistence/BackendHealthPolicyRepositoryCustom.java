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
public class BackendHealthPolicyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public BackendHealthPolicyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<BackendHealthPolicyEntity> update(
      UUID id,
      int consecutiveFailureThreshold,
      int ejectionDurationSeconds,
      int maxEjectionPercent,
      boolean enabled,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE backend_health_policies
            SET consecutive_failure_threshold = :threshold,
                ejection_duration_seconds = :duration,
                max_ejection_percent = :maxPercent,
                enabled = :enabled,
                updated_at = :updatedAt
            WHERE id = :id
            RETURNING id, api_id, name, consecutive_failure_threshold,
                      ejection_duration_seconds, max_ejection_percent,
                      enabled, created_at, updated_at
            """)
        .bind("id", id)
        .bind("threshold", consecutiveFailureThreshold)
        .bind("duration", ejectionDurationSeconds)
        .bind("maxPercent", maxEjectionPercent)
        .bind("enabled", enabled)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private BackendHealthPolicyEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new BackendHealthPolicyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("consecutive_failure_threshold", Integer.class),
        row.get("ejection_duration_seconds", Integer.class),
        row.get("max_ejection_percent", Integer.class),
        row.get("enabled", Boolean.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
