package com.autoapi.controlplane.persistence;

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
public class PolicyBundleAssignmentRepositoryCustom {

  private final DatabaseClient databaseClient;

  public PolicyBundleAssignmentRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<PolicyBundleAssignmentEntity> insert(
      UUID bundleId,
      int revisionNumber,
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId,
      boolean enabled,
      OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    var spec =
        databaseClient
            .sql(
                """
                INSERT INTO policy_bundle_assignments (
                  id, bundle_id, revision_number, scope_level, organization_id, project_id,
                  gateway_group_id, api_id, route_id, enabled, created_at, updated_at
                ) VALUES (
                  :id, :bundleId, :revisionNumber, :scopeLevel, :organizationId, :projectId,
                  :gatewayGroupId, :apiId, :routeId, :enabled, :createdAt, :updatedAt
                )
                RETURNING id, bundle_id, revision_number, scope_level, organization_id, project_id,
                          gateway_group_id, api_id, route_id, enabled, created_at, updated_at
                """)
            .bind("id", id)
            .bind("bundleId", bundleId)
            .bind("revisionNumber", revisionNumber)
            .bind("scopeLevel", scopeLevel)
            .bind("enabled", enabled)
            .bind("createdAt", now)
            .bind("updatedAt", now);
    spec = bindNullableUuid(spec, "organizationId", organizationId);
    spec = bindNullableUuid(spec, "projectId", projectId);
    spec = bindNullableUuid(spec, "gatewayGroupId", gatewayGroupId);
    spec = bindNullableUuid(spec, "apiId", apiId);
    spec = bindNullableUuid(spec, "routeId", routeId);
    return spec.map(this::mapAssignment).one();
  }

  public Mono<Boolean> disableAssignment(UUID assignmentId, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE policy_bundle_assignments
            SET enabled = false, updated_at = :updatedAt
            WHERE id = :id AND enabled = true
            """)
        .bind("id", assignmentId)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows == 1L);
  }

  public Mono<Boolean> disableByScope(
      UUID bundleId, String scopeLevel, UUID scopeResourceId, OffsetDateTime now) {
    String column = scopeColumn(scopeLevel);
    return databaseClient
        .sql(
            """
            UPDATE policy_bundle_assignments
            SET enabled = false, updated_at = :updatedAt
            WHERE bundle_id = :bundleId AND scope_level = :scopeLevel AND enabled = true
              AND """
                + column
                + " = :scopeResourceId")
        .bind("bundleId", bundleId)
        .bind("scopeLevel", scopeLevel)
        .bind("scopeResourceId", scopeResourceId)
        .bind("updatedAt", now)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows >= 1L);
  }

  public Flux<PolicyBundleAssignmentEntity> findEnabledByOrganization(UUID organizationId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = 'ORGANIZATION' AND organization_id = :organizationId AND enabled = true
            """)
        .bind("organizationId", organizationId)
        .map(this::mapAssignment)
        .all();
  }

  public Flux<PolicyBundleAssignmentEntity> findEnabledByProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = 'PROJECT' AND project_id = :projectId AND enabled = true
            """)
        .bind("projectId", projectId)
        .map(this::mapAssignment)
        .all();
  }

  public Flux<PolicyBundleAssignmentEntity> findEnabledByGatewayGroup(UUID gatewayGroupId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = 'GATEWAY_GROUP' AND gateway_group_id = :gatewayGroupId
              AND enabled = true
            """)
        .bind("gatewayGroupId", gatewayGroupId)
        .map(this::mapAssignment)
        .all();
  }

  public Flux<PolicyBundleAssignmentEntity> findEnabledByApi(UUID apiId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = 'API' AND api_id = :apiId AND enabled = true
            """)
        .bind("apiId", apiId)
        .map(this::mapAssignment)
        .all();
  }

  public Flux<PolicyBundleAssignmentEntity> findEnabledByRoute(UUID routeId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = 'ROUTE' AND route_id = :routeId AND enabled = true
            """)
        .bind("routeId", routeId)
        .map(this::mapAssignment)
        .all();
  }

  public Mono<PolicyBundleAssignmentEntity> findEnabledAssignment(
      UUID bundleId, String scopeLevel, UUID scopeResourceId) {
    String column = scopeColumn(scopeLevel);
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE bundle_id = :bundleId AND scope_level = :scopeLevel AND enabled = true
              AND """
                + column
                + " = :scopeResourceId")
        .bind("bundleId", bundleId)
        .bind("scopeLevel", scopeLevel)
        .bind("scopeResourceId", scopeResourceId)
        .map(this::mapAssignment)
        .one();
  }

  public Flux<PolicyBundleAssignmentEntity> listByScope(
      String scopeLevel, UUID scopeResourceId, int limit, int offset) {
    String column = scopeColumn(scopeLevel);
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, scope_level, organization_id, project_id,
                   gateway_group_id, api_id, route_id, enabled, created_at, updated_at
            FROM policy_bundle_assignments
            WHERE scope_level = :scopeLevel AND """
                + column
                + """
             = :scopeResourceId AND enabled = true
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
        .bind("scopeLevel", scopeLevel)
        .bind("scopeResourceId", scopeResourceId)
        .bind("limit", limit)
        .bind("offset", offset)
        .map(this::mapAssignment)
        .all();
  }

  public Mono<Long> countEnabledAssignments() {
    return databaseClient
        .sql("SELECT COUNT(*) AS cnt FROM policy_bundle_assignments WHERE enabled = true")
        .map(row -> row.get("cnt", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  private static String scopeColumn(String scopeLevel) {
    return switch (scopeLevel) {
      case "ORGANIZATION" -> "organization_id";
      case "PROJECT" -> "project_id";
      case "GATEWAY_GROUP" -> "gateway_group_id";
      case "API" -> "api_id";
      case "ROUTE" -> "route_id";
      default -> throw new IllegalArgumentException("Unknown scope level: " + scopeLevel);
    };
  }

  private PolicyBundleAssignmentEntity mapAssignment(Row row, RowMetadata metadata) {
    return new PolicyBundleAssignmentEntity(
        row.get("id", UUID.class),
        row.get("bundle_id", UUID.class),
        row.get("revision_number", Integer.class),
        row.get("scope_level", String.class),
        row.get("organization_id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("gateway_group_id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("route_id", UUID.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }

  private DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }
}
