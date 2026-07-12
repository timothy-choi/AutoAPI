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
public class RateLimitPolicyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public RateLimitPolicyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RateLimitPolicyEntity> update(
      UUID id,
      int limitCount,
      int windowSeconds,
      String identitySource,
      String redisFailureMode,
      boolean enabled,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE rate_limit_policies
            SET limit_count = :limitCount,
                window_seconds = :windowSeconds,
                identity_source = :identitySource,
                redis_failure_mode = :redisFailureMode,
                enabled = :enabled,
                updated_at = :updatedAt
            WHERE id = :id
            RETURNING id, api_id, name, limit_count, window_seconds, identity_source,
                      redis_failure_mode, enabled, created_at, updated_at
            """)
        .bind("id", id)
        .bind("limitCount", limitCount)
        .bind("windowSeconds", windowSeconds)
        .bind("identitySource", identitySource)
        .bind("redisFailureMode", redisFailureMode)
        .bind("enabled", enabled)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private RateLimitPolicyEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new RateLimitPolicyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("limit_count", Integer.class),
        row.get("window_seconds", Integer.class),
        row.get("identity_source", String.class),
        row.get("redis_failure_mode", String.class),
        row.get("enabled", Boolean.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
