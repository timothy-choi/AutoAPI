package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_audit_log")
public record PolicyAuditLogEntity(
    @Id UUID id,
    @Column("actor_principal_type") String actorPrincipalType,
    @Column("actor_principal_id") UUID actorPrincipalId,
    String action,
    @Column("scope_level") String scopeLevel,
    @Column("scope_resource_id") UUID scopeResourceId,
    @Column("policy_type") String policyType,
    @Column("bundle_id") UUID bundleId,
    @Column("bundle_revision") Integer bundleRevision,
    @Column("previous_value_json") Json previousValueJson,
    @Column("new_value_json") Json newValueJson,
    String reason,
    @Column("created_at") OffsetDateTime createdAt)
    implements NewEntity {}
