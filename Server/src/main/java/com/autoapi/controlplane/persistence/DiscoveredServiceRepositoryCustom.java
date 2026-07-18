package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
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
public class DiscoveredServiceRepositoryCustom {

  private final DatabaseClient databaseClient;

  public DiscoveredServiceRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<DiscoveredServiceEntity> incrementMembershipVersion(
      UUID serviceId, OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE discovered_services
            SET membership_version = membership_version + 1, updated_at = :updatedAt
            WHERE id = :serviceId
            RETURNING id, project_id, name, description, enabled, selection_strategy,
                      registration_mode, default_scheme, default_port, consistent_hash_key,
                      consistent_hash_key_name, membership_version, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("updatedAt", updatedAt)
        .map(this::mapService)
        .one();
  }

  public Mono<DiscoveredServiceEntity> patch(
      UUID serviceId,
      String name,
      String description,
      Boolean enabled,
      String selectionStrategy,
      String registrationMode,
      String defaultScheme,
      Integer defaultPort,
      String consistentHashKey,
      String consistentHashKeyName,
      String metadata,
      OffsetDateTime updatedAt) {
    return databaseClient
        .sql(
            """
            UPDATE discovered_services
            SET name = COALESCE(:name, name),
                description = COALESCE(:description, description),
                enabled = COALESCE(:enabled, enabled),
                selection_strategy = COALESCE(:selectionStrategy, selection_strategy),
                registration_mode = COALESCE(:registrationMode, registration_mode),
                default_scheme = COALESCE(:defaultScheme, default_scheme),
                default_port = COALESCE(:defaultPort, default_port),
                consistent_hash_key = COALESCE(:consistentHashKey, consistent_hash_key),
                consistent_hash_key_name = COALESCE(:consistentHashKeyName, consistent_hash_key_name),
                metadata = COALESCE(:metadata, metadata),
                updated_at = :updatedAt
            WHERE id = :serviceId
            RETURNING id, project_id, name, description, enabled, selection_strategy,
                      registration_mode, default_scheme, default_port, consistent_hash_key,
                      consistent_hash_key_name, membership_version, metadata, created_at, updated_at
            """)
        .bind("serviceId", serviceId)
        .bind("name", name)
        .bind("description", description)
        .bind("enabled", enabled)
        .bind("selectionStrategy", selectionStrategy)
        .bind("registrationMode", registrationMode)
        .bind("defaultScheme", defaultScheme)
        .bind("defaultPort", defaultPort)
        .bind("consistentHashKey", consistentHashKey)
        .bind("consistentHashKeyName", consistentHashKeyName)
        .bind("metadata", metadata)
        .bind("updatedAt", updatedAt)
        .map(this::mapService)
        .one();
  }

  public Flux<UUID> findAffectedApiIds(UUID serviceId) {
    return databaseClient
        .sql(
            """
            SELECT DISTINCT api_id FROM (
              SELECT r.api_id
              FROM routes r
              WHERE r.discovered_service_id = :serviceId
              UNION
              SELECT tsp.api_id
              FROM traffic_split_destinations tsd
              JOIN traffic_split_policies tsp ON tsp.id = tsd.traffic_split_policy_id
              WHERE tsd.discovered_service_id = :serviceId
            ) affected
            """)
        .bind("serviceId", serviceId)
        .map(row -> row.get("api_id", UUID.class))
        .all();
  }

  private DiscoveredServiceEntity mapService(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new DiscoveredServiceEntity(
        row.get("id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("name", String.class),
        row.get("description", String.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("selection_strategy", String.class),
        row.get("registration_mode", String.class),
        row.get("default_scheme", String.class),
        row.get("default_port", Integer.class),
        row.get("consistent_hash_key", String.class),
        row.get("consistent_hash_key_name", String.class),
        row.get("membership_version", Long.class),
        row.get("metadata", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }
}
