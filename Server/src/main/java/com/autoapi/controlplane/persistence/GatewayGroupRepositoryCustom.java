package com.autoapi.controlplane.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
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
public class GatewayGroupRepositoryCustom {

  private final DatabaseClient databaseClient;
  private final ObjectMapper objectMapper;

  public GatewayGroupRepositoryCustom(DatabaseClient databaseClient, ObjectMapper objectMapper) {
    this.databaseClient = databaseClient;
    this.objectMapper = objectMapper;
  }

  public Mono<GatewayGroupEntity> insert(
      UUID projectId,
      UUID apiId,
      String name,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      boolean enabled,
      OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    return databaseClient
        .sql(
            """
            INSERT INTO gateway_groups (
              id, project_id, api_id, name, description, region, zone, environment,
              selector, enabled, created_at, updated_at, version
            ) VALUES (
              :id, :projectId, :apiId, :name, :description, :region, :zone, :environment,
              :selector::jsonb, :enabled, :createdAt, :updatedAt, 0
            )
            RETURNING id, project_id, api_id, name, description, region, zone, environment,
                      selector::text, enabled, desired_config_version, created_at, updated_at,
                      deleted_at, version
            """)
        .bind("id", id)
        .bind("projectId", projectId)
        .bind("apiId", apiId)
        .bind("name", name)
        .bind("description", description)
        .bind("region", region)
        .bind("zone", zone)
        .bind("environment", environment)
        .bind("selector", selectorJson == null ? "{}" : selectorJson)
        .bind("enabled", enabled)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .map(this::mapGroup)
        .one();
  }

  public Mono<GatewayGroupEntity> findById(UUID projectId, UUID groupId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, api_id, name, description, region, zone, environment,
                   selector::text, enabled, desired_config_version, created_at, updated_at,
                   deleted_at, version
            FROM gateway_groups
            WHERE id = :id AND project_id = :projectId AND deleted_at IS NULL
            """)
        .bind("id", groupId)
        .bind("projectId", projectId)
        .map(this::mapGroup)
        .one();
  }

  public Flux<GatewayGroupEntity> listByProject(UUID projectId, int limit, int offset) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, api_id, name, description, region, zone, environment,
                   selector::text, enabled, desired_config_version, created_at, updated_at,
                   deleted_at, version
            FROM gateway_groups
            WHERE project_id = :projectId AND deleted_at IS NULL
            ORDER BY name ASC
            LIMIT :limit OFFSET :offset
            """)
        .bind("projectId", projectId)
        .bind("limit", limit)
        .bind("offset", offset)
        .map(this::mapGroup)
        .all();
  }

  public Mono<GatewayGroupEntity> update(
      UUID projectId,
      UUID groupId,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      Boolean enabled,
      Long desiredConfigVersion,
      long expectedVersion,
      OffsetDateTime now) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE gateway_groups
                SET description = COALESCE(:description, description),
                    region = COALESCE(:region, region),
                    zone = COALESCE(:zone, zone),
                    environment = COALESCE(:environment, environment),
                    selector = COALESCE(:selector::jsonb, selector),
                    enabled = COALESCE(:enabled, enabled),
                    desired_config_version = :desiredConfigVersion,
                    updated_at = :updatedAt,
                    version = version + 1
                WHERE id = :id AND project_id = :projectId AND deleted_at IS NULL
                  AND version = :expectedVersion
                RETURNING id, project_id, api_id, name, description, region, zone, environment,
                          selector::text, enabled, desired_config_version, created_at, updated_at,
                          deleted_at, version
                """)
            .bind("id", groupId)
            .bind("projectId", projectId)
            .bind("description", description)
            .bind("region", region)
            .bind("zone", zone)
            .bind("environment", environment)
            .bind("updatedAt", now)
            .bind("expectedVersion", expectedVersion);
    spec = bindNullableJson(spec, "selector", selectorJson);
    spec = bindNullableBoolean(spec, "enabled", enabled);
    if (desiredConfigVersion == null) {
      spec = spec.bindNull("desiredConfigVersion", Long.class);
    } else {
      spec = spec.bind("desiredConfigVersion", desiredConfigVersion);
    }
    return spec.map(this::mapGroup).one();
  }

  public Mono<Boolean> softDelete(UUID projectId, UUID groupId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE gateway_groups
            SET deleted_at = :deletedAt, updated_at = :updatedAt, enabled = false
            WHERE id = :id AND project_id = :projectId AND deleted_at IS NULL
            """)
        .bind("deletedAt", now)
        .bind("updatedAt", now)
        .bind("id", groupId)
        .bind("projectId", projectId)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows == 1L);
  }

  public Mono<Boolean> hasActiveRollout(UUID groupId) {
    return databaseClient
        .sql(
            """
            SELECT COUNT(*) AS cnt FROM runtime_rollouts
            WHERE gateway_group_id = :groupId
              AND status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK')
            """)
        .bind("groupId", groupId)
        .map(row -> row.get("cnt", Long.class) > 0)
        .one()
        .defaultIfEmpty(false);
  }

  public Flux<GatewayGroupEntity> listEnabledByProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, api_id, name, description, region, zone, environment,
                   selector::text, enabled, desired_config_version, created_at, updated_at,
                   deleted_at, version
            FROM gateway_groups
            WHERE project_id = :projectId AND deleted_at IS NULL AND enabled = true
            ORDER BY name ASC
            """)
        .bind("projectId", projectId)
        .map(this::mapGroup)
        .all();
  }

  public Mono<Void> upsertExplicitMembership(
      UUID projectId, UUID groupId, String gatewayId, String membershipType, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            INSERT INTO gateway_group_memberships (
              id, project_id, gateway_group_id, gateway_id, membership_type, created_at, updated_at
            ) VALUES (
              :id, :projectId, :groupId, :gatewayId, :membershipType, :createdAt, :updatedAt
            )
            ON CONFLICT (project_id, gateway_id) DO UPDATE SET
              gateway_group_id = EXCLUDED.gateway_group_id,
              membership_type = EXCLUDED.membership_type,
              updated_at = EXCLUDED.updated_at
            """)
        .bind("id", UUID.randomUUID())
        .bind("projectId", projectId)
        .bind("groupId", groupId)
        .bind("gatewayId", gatewayId)
        .bind("membershipType", membershipType)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .then();
  }

  public Mono<Boolean> removeExplicitMembership(UUID projectId, UUID groupId, String gatewayId) {
    return databaseClient
        .sql(
            """
            DELETE FROM gateway_group_memberships
            WHERE project_id = :projectId AND gateway_group_id = :groupId AND gateway_id = :gatewayId
            """)
        .bind("projectId", projectId)
        .bind("groupId", groupId)
        .bind("gatewayId", gatewayId)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows >= 0L);
  }

  public Flux<GatewayGroupMembershipRow> listMembershipsByProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, gateway_group_id, gateway_id, membership_type, created_at, updated_at
            FROM gateway_group_memberships
            WHERE project_id = :projectId
            """)
        .bind("projectId", projectId)
        .map(
            row ->
                new GatewayGroupMembershipRow(
                    row.get("id", UUID.class),
                    row.get("project_id", UUID.class),
                    row.get("gateway_group_id", UUID.class),
                    row.get("gateway_id", String.class),
                    row.get("membership_type", String.class),
                    row.get("created_at", OffsetDateTime.class),
                    row.get("updated_at", OffsetDateTime.class)))
        .all();
  }

  public Mono<Void> setDesiredConfigVersion(
      UUID groupId, long desiredVersion, long expectedVersion, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE gateway_groups
            SET desired_config_version = :desiredVersion,
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id AND version = :expectedVersion AND deleted_at IS NULL
            """)
        .bind("desiredVersion", desiredVersion)
        .bind("updatedAt", now)
        .bind("id", groupId)
        .bind("expectedVersion", expectedVersion)
        .fetch()
        .rowsUpdated()
        .then();
  }

  private GatewayGroupEntity mapGroup(Row row, io.r2dbc.spi.RowMetadata metadata) {
    return new GatewayGroupEntity(
        row.get("id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("region", String.class),
        row.get("zone", String.class),
        row.get("environment", String.class),
        row.get("selector", String.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("desired_config_version", Long.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("deleted_at", OffsetDateTime.class),
        row.get("version", Long.class));
  }

  private DatabaseClient.GenericExecuteSpec bindNullableJson(
      DatabaseClient.GenericExecuteSpec spec, String name, String json) {
    return json == null ? spec.bindNull(name, String.class) : spec.bind(name, json);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableBoolean(
      DatabaseClient.GenericExecuteSpec spec, String name, Boolean value) {
    return value == null ? spec.bindNull(name, Boolean.class) : spec.bind(name, value);
  }

  public record GatewayGroupMembershipRow(
      UUID id,
      UUID projectId,
      UUID gatewayGroupId,
      String gatewayId,
      String membershipType,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
