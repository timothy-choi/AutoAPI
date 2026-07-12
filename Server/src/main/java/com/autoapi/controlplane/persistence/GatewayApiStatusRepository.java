package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayApiStatusRepository {

  private final DatabaseClient databaseClient;

  public GatewayApiStatusRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Flux<GatewayApiStatusEntity> findByApiId(UUID apiId) {
    return databaseClient
        .sql("SELECT * FROM gateway_api_status WHERE api_id = :apiId")
        .bind("apiId", apiId)
        .map(this::mapRow)
        .all();
  }

  public Flux<GatewayApiStatusEntity> findByGatewayId(String gatewayId) {
    return databaseClient
        .sql("SELECT * FROM gateway_api_status WHERE gateway_id = :gatewayId")
        .bind("gatewayId", gatewayId)
        .map(this::mapRow)
        .all();
  }

  public Mono<GatewayApiStatusEntity> findByGatewayIdAndApiId(String gatewayId, UUID apiId) {
    return databaseClient
        .sql("SELECT * FROM gateway_api_status WHERE gateway_id = :gatewayId AND api_id = :apiId")
        .bind("gatewayId", gatewayId)
        .bind("apiId", apiId)
        .map(this::mapRow)
        .one();
  }

  private GatewayApiStatusEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new GatewayApiStatusEntity(
        row.get("gateway_id", String.class),
        row.get("api_id", UUID.class),
        readNullableLong(row, "active_version"),
        row.get("active_content_hash", String.class),
        readNullableLong(row, "last_attempted_version"),
        row.get("last_attempted_content_hash", String.class),
        row.get("last_status", String.class),
        row.get("last_error_code", String.class),
        row.get("last_diagnostic", String.class),
        readNullableLong(row, "last_apply_duration_ms"),
        row.get("last_reported_at", java.time.OffsetDateTime.class),
        row.get("created_at", java.time.OffsetDateTime.class),
        row.get("updated_at", java.time.OffsetDateTime.class));
  }

  private static Long readNullableLong(io.r2dbc.spi.Readable row, String column) {
    Object value = row.get(column);
    if (value == null) {
      return null;
    }
    if (value instanceof Long longValue) {
      return longValue;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new IllegalStateException(
        "Unexpected type for column " + column + ": " + value.getClass().getName());
  }
}
