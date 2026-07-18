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
public class TrafficSplitDestinationRepositoryCustom {

  private final DatabaseClient databaseClient;

  public TrafficSplitDestinationRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<TrafficSplitDestinationEntity> patchDestination(
      UUID destinationId,
      String name,
      int weight,
      int priority,
      boolean primary,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE traffic_split_destinations
            SET name = :name,
                weight = :weight,
                priority = :priority,
                is_primary = :primary,
                updated_at = :updatedAt
            WHERE id = :destinationId
            RETURNING id, traffic_split_policy_id, upstream_pool_id, discovered_service_id, name,
                      weight, priority, is_primary, created_at, updated_at
            """)
        .bind("destinationId", destinationId)
        .bind("name", name)
        .bind("weight", weight)
        .bind("priority", priority)
        .bind("primary", primary)
        .bind("updatedAt", updatedAt)
        .map(this::mapDestination)
        .one();
  }

  private TrafficSplitDestinationEntity mapDestination(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new TrafficSplitDestinationEntity(
        row.get("id", UUID.class),
        row.get("traffic_split_policy_id", UUID.class),
        row.get("upstream_pool_id", UUID.class),
        row.get("discovered_service_id", UUID.class),
        row.get("name", String.class),
        row.get("weight", Integer.class),
        row.get("priority", Integer.class),
        Boolean.TRUE.equals(row.get("is_primary", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
