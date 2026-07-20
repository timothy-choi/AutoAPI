package com.autoapi.controlplane.persistence;

import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_bundle_revisions")
public record PolicyBundleRevisionEntity(
    @Id UUID id,
    @Column("bundle_id") UUID bundleId,
    @Column("revision_number") int revisionNumber,
    @Column("content_json") Json contentJson,
    String message,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("created_by_principal_type") String createdByPrincipalType,
    @Column("created_by_principal_id") UUID createdByPrincipalId)
    implements NewEntity {}
