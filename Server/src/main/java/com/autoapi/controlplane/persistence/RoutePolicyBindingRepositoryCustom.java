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
public class RoutePolicyBindingRepositoryCustom {

  private final DatabaseClient databaseClient;

  public RoutePolicyBindingRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RoutePolicyBindingEntity> bindRetryPolicy(
      UUID routeId, UUID retryPolicyId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET retry_policy_id = :retryPolicyId,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            RETURNING route_id, authentication_required, rate_limit_policy_id,
                      retry_policy_id, created_at, updated_at
            """)
        .bind("routeId", routeId)
        .bind("retryPolicyId", retryPolicyId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<RoutePolicyBindingEntity> clearRetryPolicy(UUID routeId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET retry_policy_id = NULL,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            RETURNING route_id, authentication_required, rate_limit_policy_id,
                      retry_policy_id, created_at, updated_at
            """)
        .bind("routeId", routeId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private RoutePolicyBindingEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new RoutePolicyBindingEntity(
        row.get("route_id", UUID.class),
        Boolean.TRUE.equals(row.get("authentication_required", Boolean.class)),
        row.get("rate_limit_policy_id", UUID.class),
        row.get("retry_policy_id", UUID.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
