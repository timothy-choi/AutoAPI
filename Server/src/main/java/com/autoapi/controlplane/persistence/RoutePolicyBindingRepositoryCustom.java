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

  private static final String RETURNING_CLAUSE =
      """
      RETURNING route_id, authentication_required, rate_limit_policy_id,
                retry_policy_id, traffic_split_policy_id, circuit_breaker_policy_id,
                created_at, updated_at
      """;

  private final DatabaseClient databaseClient;

  public RoutePolicyBindingRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RoutePolicyBindingEntity> bindAuthenticationAndRateLimit(
      UUID routeId,
      boolean authenticationRequired,
      UUID rateLimitPolicyId,
      OffsetDateTime updatedAt) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE route_policy_bindings
                SET authentication_required = :authenticationRequired,
                    rate_limit_policy_id = :rateLimitPolicyId,
                    updated_at = :updatedAt
                WHERE route_id = :routeId
                """
                    + RETURNING_CLAUSE)
            .bind("routeId", routeId)
            .bind("authenticationRequired", authenticationRequired)
            .bind("updatedAt", updatedAt);
    if (rateLimitPolicyId == null) {
      spec = spec.bindNull("rateLimitPolicyId", UUID.class);
    } else {
      spec = spec.bind("rateLimitPolicyId", rateLimitPolicyId);
    }
    return spec.map(this::mapRow).one();
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
            """
                + RETURNING_CLAUSE)
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
            """
                + RETURNING_CLAUSE)
        .bind("routeId", routeId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<RoutePolicyBindingEntity> bindTrafficSplitPolicy(
      UUID routeId, UUID trafficSplitPolicyId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET traffic_split_policy_id = :trafficSplitPolicyId,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            """
                + RETURNING_CLAUSE)
        .bind("routeId", routeId)
        .bind("trafficSplitPolicyId", trafficSplitPolicyId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<RoutePolicyBindingEntity> clearTrafficSplitPolicy(
      UUID routeId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET traffic_split_policy_id = NULL,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            """
                + RETURNING_CLAUSE)
        .bind("routeId", routeId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<RoutePolicyBindingEntity> bindCircuitBreakerPolicy(
      UUID routeId, UUID circuitBreakerPolicyId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET circuit_breaker_policy_id = :circuitBreakerPolicyId,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            """
                + RETURNING_CLAUSE)
        .bind("routeId", routeId)
        .bind("circuitBreakerPolicyId", circuitBreakerPolicyId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<RoutePolicyBindingEntity> clearCircuitBreakerPolicy(
      UUID routeId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE route_policy_bindings
            SET circuit_breaker_policy_id = NULL,
                updated_at = :updatedAt
            WHERE route_id = :routeId
            """
                + RETURNING_CLAUSE)
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
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("retry_policy_id", UUID.class),
        row.get("traffic_split_policy_id", UUID.class),
        row.get("circuit_breaker_policy_id", UUID.class));
  }
}
