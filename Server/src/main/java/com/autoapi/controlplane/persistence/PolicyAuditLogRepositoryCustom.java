package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
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
public class PolicyAuditLogRepositoryCustom {

  private final DatabaseClient databaseClient;

  public PolicyAuditLogRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<PolicyAuditLogEntity> insert(
      String actorPrincipalType,
      UUID actorPrincipalId,
      String action,
      String scopeLevel,
      UUID scopeResourceId,
      String policyType,
      UUID bundleId,
      Integer bundleRevision,
      String previousValueJson,
      String newValueJson,
      String reason,
      OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    var spec =
        databaseClient
            .sql(
                """
                INSERT INTO policy_audit_log (
                  id, actor_principal_type, actor_principal_id, action, scope_level,
                  scope_resource_id, policy_type, bundle_id, bundle_revision,
                  previous_value_json, new_value_json, reason, created_at
                ) VALUES (
                  :id, :actorType, :actorId, :action, :scopeLevel, :scopeResourceId, :policyType,
                  :bundleId, :bundleRevision, :previousJson::jsonb, :newJson::jsonb, :reason, :createdAt
                )
                RETURNING id, actor_principal_type, actor_principal_id, action, scope_level,
                          scope_resource_id, policy_type, bundle_id, bundle_revision,
                          previous_value_json, new_value_json, reason, created_at
                """)
            .bind("id", id)
            .bind("action", action)
            .bind("createdAt", now);
    spec = bindNullableString(spec, "actorType", actorPrincipalType);
    spec = bindNullableUuid(spec, "actorId", actorPrincipalId);
    spec = bindNullableString(spec, "scopeLevel", scopeLevel);
    spec = bindNullableUuid(spec, "scopeResourceId", scopeResourceId);
    spec = bindNullableString(spec, "policyType", policyType);
    spec = bindNullableUuid(spec, "bundleId", bundleId);
    spec = bindNullableInteger(spec, "bundleRevision", bundleRevision);
    spec = bindNullableJson(spec, "previousJson", previousValueJson);
    spec = bindNullableJson(spec, "newJson", newValueJson);
    spec = bindNullableString(spec, "reason", reason);
    return spec.map(this::mapRow).one();
  }

  private PolicyAuditLogEntity mapRow(Row row, RowMetadata metadata) {
    return new PolicyAuditLogEntity(
        row.get("id", UUID.class),
        row.get("actor_principal_type", String.class),
        row.get("actor_principal_id", UUID.class),
        row.get("action", String.class),
        row.get("scope_level", String.class),
        row.get("scope_resource_id", UUID.class),
        row.get("policy_type", String.class),
        row.get("bundle_id", UUID.class),
        row.get("bundle_revision", Integer.class),
        row.get("previous_value_json", Json.class),
        row.get("new_value_json", Json.class),
        row.get("reason", String.class),
        row.get("created_at", OffsetDateTime.class));
  }

  private DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableInteger(
      DatabaseClient.GenericExecuteSpec spec, String name, Integer value) {
    return value == null ? spec.bindNull(name, Integer.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableJson(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }
}
