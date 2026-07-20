package com.autoapi.controlplane.persistence;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GlobalPolicyCacheGenerationRepositoryCustom {

  private static final int SINGLETON_ID = 1;

  private final DatabaseClient databaseClient;

  public GlobalPolicyCacheGenerationRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Long> getGeneration() {
    return databaseClient
        .sql(
            """
            SELECT generation FROM global_policy_cache_generation WHERE id = :id
            """)
        .bind("id", SINGLETON_ID)
        .map(row -> row.get("generation", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  public Mono<Long> bumpGeneration(OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE global_policy_cache_generation
            SET generation = generation + 1, updated_at = :updatedAt
            WHERE id = :id
            RETURNING generation
            """)
        .bind("id", SINGLETON_ID)
        .bind("updatedAt", now)
        .map(row -> row.get("generation", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  public Mono<GlobalPolicyCacheGenerationEntity> findSingleton() {
    return databaseClient
        .sql(
            """
            SELECT id, generation, updated_at
            FROM global_policy_cache_generation
            WHERE id = :id
            """)
        .bind("id", SINGLETON_ID)
        .map(this::mapRow)
        .one();
  }

  private GlobalPolicyCacheGenerationEntity mapRow(Row row, RowMetadata metadata) {
    return new GlobalPolicyCacheGenerationEntity(
        row.get("id", Integer.class),
        row.get("generation", Long.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
