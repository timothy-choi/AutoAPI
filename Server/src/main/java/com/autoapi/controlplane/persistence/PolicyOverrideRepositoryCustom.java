package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
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
public class PolicyOverrideRepositoryCustom {

  private final DatabaseClient databaseClient;

  public PolicyOverrideRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<PolicyOverrideEntity> insert(
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId,
      String policyType,
      String mode,
      String contentJson,
      OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    var spec =
        databaseClient
            .sql(
                """
                INSERT INTO policy_overrides (
                  id, scope_level, organization_id, project_id, gateway_group_id, api_id, route_id,
                  policy_type, mode, content_json, created_at, updated_at
                ) VALUES (
                  :id, :scopeLevel, :organizationId, :projectId, :gatewayGroupId, :apiId, :routeId,
                  :policyType, :mode, :contentJson::jsonb, :createdAt, :updatedAt
                )
                RETURNING id, scope_level, organization_id, project_id, gateway_group_id, api_id,
                          route_id, policy_type, mode, content_json::text AS content_json,
                          created_at, updated_at
                """)
            .bind("id", id)
            .bind("scopeLevel", scopeLevel)
            .bind("policyType", policyType)
            .bind("mode", mode)
            .bind("createdAt", now)
            .bind("updatedAt", now);
    spec = bindNullableUuid(spec, "organizationId", organizationId);
    spec = bindNullableUuid(spec, "projectId", projectId);
    spec = bindNullableUuid(spec, "gatewayGroupId", gatewayGroupId);
    spec = bindNullableUuid(spec, "apiId", apiId);
    spec = bindNullableUuid(spec, "routeId", routeId);
    if (contentJson == null) {
      spec = spec.bindNull("contentJson", String.class);
    } else {
      spec = spec.bind("contentJson", contentJson);
    }
    return spec.map(this::mapOverride).one();
  }

  public Mono<PolicyOverrideEntity> update(
      UUID overrideId, String mode, String contentJson, OffsetDateTime now) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE policy_overrides
                SET mode = COALESCE(:mode, mode),
                    content_json = COALESCE(:contentJson::jsonb, content_json),
                    updated_at = :updatedAt
                WHERE id = :id
                RETURNING id, scope_level, organization_id, project_id, gateway_group_id, api_id,
                          route_id, policy_type, mode, content_json::text AS content_json,
                          created_at, updated_at
                """)
            .bind("id", overrideId)
            .bind("updatedAt", now);
    spec = bindNullableString(spec, "mode", mode);
    if (contentJson == null) {
      spec = spec.bindNull("contentJson", String.class);
    } else {
      spec = spec.bind("contentJson", contentJson);
    }
    return spec.map(this::mapOverride).one();
  }

  public Mono<Boolean> delete(UUID overrideId) {
    return databaseClient
        .sql("DELETE FROM policy_overrides WHERE id = :id")
        .bind("id", overrideId)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows == 1L);
  }

  public Mono<PolicyOverrideEntity> findById(UUID overrideId) {
    return databaseClient
        .sql(
            """
            SELECT id, scope_level, organization_id, project_id, gateway_group_id, api_id, route_id,
                   policy_type, mode, content_json::text AS content_json, created_at, updated_at
            FROM policy_overrides
            WHERE id = :id
            """)
        .bind("id", overrideId)
        .map(this::mapOverride)
        .one();
  }

  public Flux<PolicyOverrideEntity> findByOrganization(UUID organizationId) {
    return findByScope("ORGANIZATION", "organization_id", organizationId);
  }

  public Flux<PolicyOverrideEntity> findByProject(UUID projectId) {
    return findByScope("PROJECT", "project_id", projectId);
  }

  public Flux<PolicyOverrideEntity> findByGatewayGroup(UUID gatewayGroupId) {
    return findByScope("GATEWAY_GROUP", "gateway_group_id", gatewayGroupId);
  }

  public Flux<PolicyOverrideEntity> findByApi(UUID apiId) {
    return findByScope("API", "api_id", apiId);
  }

  public Flux<PolicyOverrideEntity> findByRoute(UUID routeId) {
    return findByScope("ROUTE", "route_id", routeId);
  }

  public Mono<Long> countAll() {
    return databaseClient
        .sql("SELECT COUNT(*) AS cnt FROM policy_overrides")
        .map(row -> row.get("cnt", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  private Flux<PolicyOverrideEntity> findByScope(
      String scopeLevel, String column, UUID resourceId) {
    return databaseClient
        .sql(
            """
            SELECT id, scope_level, organization_id, project_id, gateway_group_id, api_id, route_id,
                   policy_type, mode, content_json::text AS content_json, created_at, updated_at
            FROM policy_overrides
            WHERE scope_level = :scopeLevel"""
                + " AND "
                + column
                + " = :resourceId")
        .bind("scopeLevel", scopeLevel)
        .bind("resourceId", resourceId)
        .map(this::mapOverride)
        .all();
  }

  private PolicyOverrideEntity mapOverride(Row row, RowMetadata metadata) {
    return new PolicyOverrideEntity(
        row.get("id", UUID.class),
        row.get("scope_level", String.class),
        row.get("organization_id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("gateway_group_id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("route_id", UUID.class),
        row.get("policy_type", String.class),
        row.get("mode", String.class),
        readJson(row, "content_json"),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }

  private static Json readJson(Row row, String column) {
    String raw = row.get(column, String.class);
    return raw == null ? null : Json.of(raw);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }
}
