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
public class ApiKeyRepositoryCustom {

  private final DatabaseClient databaseClient;

  public ApiKeyRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<ApiKeyEntity> revoke(UUID id, OffsetDateTime revokedAt, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE api_keys
            SET enabled = false, revoked_at = :revokedAt, updated_at = :updatedAt
            WHERE id = :id
            RETURNING id, api_id, key_id, name, key_prefix, secret_digest, enabled, expires_at,
                      created_at, updated_at, revoked_at
            """)
        .bind("id", id)
        .bind("revokedAt", revokedAt)
        .bind("updatedAt", updatedAt)
        .map(this::mapRow)
        .one();
  }

  private ApiKeyEntity mapRow(io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new ApiKeyEntity(
        row.get("id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("key_id", String.class),
        row.get("name", String.class),
        row.get("key_prefix", String.class),
        row.get("secret_digest", byte[].class),
        row.get("enabled", Boolean.class),
        row.get("expires_at", OffsetDateTime.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("revoked_at", OffsetDateTime.class));
  }
}
