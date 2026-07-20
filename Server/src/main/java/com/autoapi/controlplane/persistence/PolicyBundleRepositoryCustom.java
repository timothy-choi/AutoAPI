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
public class PolicyBundleRepositoryCustom {

  private final DatabaseClient databaseClient;

  public PolicyBundleRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<PolicyBundleEntity> insert(
      UUID organizationId, String name, String description, boolean enabled, OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    var spec =
        databaseClient
            .sql(
                """
                INSERT INTO policy_bundles (
                  id, organization_id, name, description, enabled, created_at, updated_at
                ) VALUES (
                  :id, :organizationId, :name, :description, :enabled, :createdAt, :updatedAt
                )
                RETURNING id, organization_id, name, description, enabled, created_at, updated_at
                """)
            .bind("id", id)
            .bind("organizationId", organizationId)
            .bind("name", name)
            .bind("enabled", enabled)
            .bind("createdAt", now)
            .bind("updatedAt", now);
    spec = bindNullableString(spec, "description", description);
    return spec.map(this::mapBundle).one();
  }

  public Mono<PolicyBundleEntity> update(
      UUID organizationId, UUID bundleId, String description, Boolean enabled, OffsetDateTime now) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE policy_bundles
                SET description = COALESCE(:description, description),
                    enabled = COALESCE(:enabled, enabled),
                    updated_at = :updatedAt
                WHERE id = :id AND organization_id = :organizationId
                RETURNING id, organization_id, name, description, enabled, created_at, updated_at
                """)
            .bind("id", bundleId)
            .bind("organizationId", organizationId)
            .bind("updatedAt", now);
    spec = bindNullableString(spec, "description", description);
    spec = bindNullableBoolean(spec, "enabled", enabled);
    return spec.map(this::mapBundle).one();
  }

  public Flux<PolicyBundleEntity> listByOrganization(UUID organizationId, int limit, int offset) {
    return databaseClient
        .sql(
            """
            SELECT id, organization_id, name, description, enabled, created_at, updated_at
            FROM policy_bundles
            WHERE organization_id = :organizationId
            ORDER BY name ASC
            LIMIT :limit OFFSET :offset
            """)
        .bind("organizationId", organizationId)
        .bind("limit", limit)
        .bind("offset", offset)
        .map(this::mapBundle)
        .all();
  }

  public Mono<PolicyBundleEntity> findByOrganizationAndId(UUID organizationId, UUID bundleId) {
    return databaseClient
        .sql(
            """
            SELECT id, organization_id, name, description, enabled, created_at, updated_at
            FROM policy_bundles
            WHERE id = :id AND organization_id = :organizationId
            """)
        .bind("id", bundleId)
        .bind("organizationId", organizationId)
        .map(this::mapBundle)
        .one();
  }

  public Mono<Integer> nextRevisionNumber(UUID bundleId) {
    return databaseClient
        .sql(
            """
            SELECT COALESCE(MAX(revision_number), 0) + 1 AS next_revision
            FROM policy_bundle_revisions
            WHERE bundle_id = :bundleId
            """)
        .bind("bundleId", bundleId)
        .map(row -> row.get("next_revision", Integer.class))
        .one()
        .defaultIfEmpty(1);
  }

  public Mono<PolicyBundleRevisionEntity> insertRevision(
      UUID bundleId,
      int revisionNumber,
      String contentJson,
      String message,
      OffsetDateTime now,
      String principalType,
      UUID principalId) {
    UUID id = UUID.randomUUID();
    var spec =
        databaseClient
            .sql(
                """
                INSERT INTO policy_bundle_revisions (
                  id, bundle_id, revision_number, content_json, message, created_at,
                  created_by_principal_type, created_by_principal_id
                ) VALUES (
                  :id, :bundleId, :revisionNumber, :contentJson::jsonb, :message, :createdAt,
                  :principalType, :principalId
                )
                RETURNING id, bundle_id, revision_number, content_json, message, created_at,
                          created_by_principal_type, created_by_principal_id
                """)
            .bind("id", id)
            .bind("bundleId", bundleId)
            .bind("revisionNumber", revisionNumber)
            .bind("contentJson", contentJson == null ? "{}" : contentJson)
            .bind("createdAt", now);
    spec = bindNullableString(spec, "message", message);
    spec = bindNullableString(spec, "principalType", principalType);
    spec = bindNullableUuid(spec, "principalId", principalId);
    return spec.map(this::mapRevision).one();
  }

  public Flux<PolicyBundleRevisionEntity> listRevisions(UUID bundleId, int limit, int offset) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, content_json, message, created_at,
                   created_by_principal_type, created_by_principal_id
            FROM policy_bundle_revisions
            WHERE bundle_id = :bundleId
            ORDER BY revision_number DESC
            LIMIT :limit OFFSET :offset
            """)
        .bind("bundleId", bundleId)
        .bind("limit", limit)
        .bind("offset", offset)
        .map(this::mapRevision)
        .all();
  }

  public Mono<PolicyBundleRevisionEntity> findRevision(UUID bundleId, int revisionNumber) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, content_json, message, created_at,
                   created_by_principal_type, created_by_principal_id
            FROM policy_bundle_revisions
            WHERE bundle_id = :bundleId AND revision_number = :revisionNumber
            """)
        .bind("bundleId", bundleId)
        .bind("revisionNumber", revisionNumber)
        .map(this::mapRevision)
        .one();
  }

  public Mono<PolicyBundleRevisionEntity> findRevisionById(UUID revisionId) {
    return databaseClient
        .sql(
            """
            SELECT id, bundle_id, revision_number, content_json, message, created_at,
                   created_by_principal_type, created_by_principal_id
            FROM policy_bundle_revisions
            WHERE id = :id
            """)
        .bind("id", revisionId)
        .map(this::mapRevision)
        .one();
  }

  public Mono<PolicyBundleEntity> findById(UUID bundleId) {
    return databaseClient
        .sql(
            """
            SELECT id, organization_id, name, description, enabled, created_at, updated_at
            FROM policy_bundles
            WHERE id = :id
            """)
        .bind("id", bundleId)
        .map(this::mapBundle)
        .one();
  }

  private PolicyBundleEntity mapBundle(Row row, RowMetadata metadata) {
    return new PolicyBundleEntity(
        row.get("id", UUID.class),
        row.get("organization_id", UUID.class),
        row.get("name", String.class),
        row.get("description", String.class),
        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class));
  }

  private PolicyBundleRevisionEntity mapRevision(Row row, RowMetadata metadata) {
    return new PolicyBundleRevisionEntity(
        row.get("id", UUID.class),
        row.get("bundle_id", UUID.class),
        row.get("revision_number", Integer.class),
        row.get("content_json", Json.class),
        row.get("message", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("created_by_principal_type", String.class),
        row.get("created_by_principal_id", UUID.class));
  }

  private DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableBoolean(
      DatabaseClient.GenericExecuteSpec spec, String name, Boolean value) {
    return value == null ? spec.bindNull(name, Boolean.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }
}
