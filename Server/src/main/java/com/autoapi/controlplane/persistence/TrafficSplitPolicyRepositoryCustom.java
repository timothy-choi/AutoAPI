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
public class TrafficSplitPolicyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public TrafficSplitPolicyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<TrafficSplitPolicyEntity> patchPolicy(
      UUID policyId,
      String name,
      String selectionKey,
      String selectionKeyName,
      String fallbackMode,
      boolean enabled,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE traffic_split_policies
            SET name = :name,
                selection_key = :selectionKey,
                selection_key_name = :selectionKeyName,
                fallback_mode = :fallbackMode,
                enabled = :enabled,
                updated_at = :updatedAt
            WHERE id = :policyId
            RETURNING id, api_id, name, selection_key, selection_key_name, fallback_mode,
                      enabled, created_at, updated_at
            """)
        .bind("policyId", policyId)
        .bind("name", name)
        .bind("selectionKey", selectionKey)
        .bind("selectionKeyName", selectionKeyName)
        .bind("fallbackMode", fallbackMode)
        .bind("enabled", enabled)
        .bind("updatedAt", updatedAt)
        .map(this::mapPolicy)
        .one();
  }

  private TrafficSplitPolicyEntity mapPolicy(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new TrafficSplitPolicyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("selection_key", String.class),
        row.get("selection_key_name", String.class),
        row.get("fallback_mode", String.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
