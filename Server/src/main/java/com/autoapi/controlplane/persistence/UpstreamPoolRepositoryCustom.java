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
public class UpstreamPoolRepositoryCustom {

  private final DatabaseClient databaseClient;

  public UpstreamPoolRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<UpstreamPoolEntity> updateBackendHealthPolicy(
      UUID poolId, UUID backendHealthPolicyId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE upstream_pools
            SET backend_health_policy_id = :policyId, updated_at = :updatedAt
            WHERE id = :poolId
            RETURNING id, api_id, name, load_balancing, backend_health_policy_id,
                      created_at, updated_at
            """)
        .bind("poolId", poolId)
        .bind("policyId", backendHealthPolicyId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<UpstreamPoolEntity> clearBackendHealthPolicy(UUID poolId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE upstream_pools
            SET backend_health_policy_id = NULL, updated_at = :updatedAt
            WHERE id = :poolId
            RETURNING id, api_id, name, load_balancing, backend_health_policy_id,
                      created_at, updated_at
            """)
        .bind("poolId", poolId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private UpstreamPoolEntity mapRow(io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new UpstreamPoolEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("load_balancing", String.class),
        row.get("backend_health_policy_id", UUID.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
