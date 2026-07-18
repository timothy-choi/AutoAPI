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
public class RouteRepositoryCustom {

  private final DatabaseClient databaseClient;

  public RouteRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RouteEntity> clearUpstreamPool(UUID routeId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE routes
            SET upstream_pool_id = NULL,
                updated_at = :updatedAt
            WHERE id = :routeId
            RETURNING id, api_id, name, host, path_prefix, methods, upstream_pool_id,
                      enabled, created_at, updated_at
            """)
        .bind("routeId", routeId)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private RouteEntity mapRow(io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new RouteEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("host", String.class),
        row.get("path_prefix", String.class),
        row.get("methods", String[].class),
        row.get("upstream_pool_id", UUID.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
