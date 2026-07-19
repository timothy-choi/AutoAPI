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
public class ManagementAccessCredentialRepositoryCustom {

  private final DatabaseClient databaseClient;

  public ManagementAccessCredentialRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<ManagementAccessCredentialEntity> revoke(
      UUID id, OffsetDateTime revokedAt, String status) {
    return databaseClient
        .sql(
            """
            UPDATE management_access_credentials
            SET status = :status, revoked_at = :revokedAt
            WHERE id = :id
            RETURNING id, public_token_id, principal_type, principal_id, organization_id, name,
                      description, secret_digest, digest_key_version, scopes, status, created_at,
                      expires_at, last_used_at, last_used_source, revoked_at,
                      rotated_from_credential_id
            """)
        .bind("id", id)
        .bind("status", status)
        .bind("revokedAt", revokedAt)
        .map(this::mapRow)
        .one();
  }

  public Mono<ManagementAccessCredentialEntity> markRotated(UUID id, OffsetDateTime revokedAt) {
    return revoke(id, revokedAt, "ROTATED");
  }

  public Mono<ManagementAccessCredentialEntity> insert(ManagementAccessCredentialEntity entity) {
    DatabaseClient.GenericExecuteSpec spec =
        databaseClient
            .sql(
                """
                INSERT INTO management_access_credentials (
                    id, public_token_id, principal_type, principal_id, organization_id, name,
                    description, secret_digest, digest_key_version, scopes, status, created_at,
                    expires_at, last_used_at, last_used_source, revoked_at, rotated_from_credential_id
                ) VALUES (
                    :id, :publicTokenId, :principalType, :principalId, :organizationId, :name,
                    :description, :secretDigest, :digestKeyVersion, :scopes::jsonb, :status, :createdAt,
                    :expiresAt, :lastUsedAt, :lastUsedSource, :revokedAt, :rotatedFromCredentialId
                )
                RETURNING id, public_token_id, principal_type, principal_id, organization_id, name,
                          description, secret_digest, digest_key_version, scopes, status, created_at,
                          expires_at, last_used_at, last_used_source, revoked_at,
                          rotated_from_credential_id
                """)
            .bind("id", entity.id())
            .bind("publicTokenId", entity.publicTokenId())
            .bind("principalType", entity.principalType())
            .bind("principalId", entity.principalId())
            .bind("organizationId", entity.organizationId())
            .bind("name", entity.name())
            .bind("secretDigest", entity.secretDigest())
            .bind("digestKeyVersion", entity.digestKeyVersion())
            .bind("scopes", entity.scopes())
            .bind("status", entity.status())
            .bind("createdAt", entity.createdAt());
    spec = bindNullableString(spec, "description", entity.description());
    spec = bindNullable(spec, "expiresAt", entity.expiresAt(), OffsetDateTime.class);
    spec = bindNullable(spec, "lastUsedAt", entity.lastUsedAt(), OffsetDateTime.class);
    spec = bindNullableString(spec, "lastUsedSource", entity.lastUsedSource());
    spec = bindNullable(spec, "revokedAt", entity.revokedAt(), OffsetDateTime.class);
    spec =
        bindNullable(spec, "rotatedFromCredentialId", entity.rotatedFromCredentialId(), UUID.class);
    return spec.map(this::mapRow).one();
  }

  private static DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
      DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
    return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
  }

  public Mono<Boolean> touchLastUsedIfStale(
      UUID id, OffsetDateTime now, OffsetDateTime staleBefore, String source) {
    return databaseClient
        .sql(
            """
            UPDATE management_access_credentials
            SET last_used_at = :now, last_used_source = :source
            WHERE id = :id
              AND status = 'ACTIVE'
              AND (last_used_at IS NULL OR last_used_at < :staleBefore)
            """)
        .bind("id", id)
        .bind("now", now)
        .bind("source", source)
        .bind("staleBefore", staleBefore)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows != null && rows > 0);
  }

  private ManagementAccessCredentialEntity mapRow(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new ManagementAccessCredentialEntity(
        row.get("id", UUID.class),
        row.get("public_token_id", String.class),
        row.get("principal_type", String.class),
        row.get("principal_id", UUID.class),
        row.get("organization_id", UUID.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("secret_digest", byte[].class),
        row.get("digest_key_version", Integer.class),
        row.get("scopes", String.class),
        row.get("status", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("expires_at", OffsetDateTime.class),
        row.get("last_used_at", OffsetDateTime.class),
        row.get("last_used_source", String.class),
        row.get("revoked_at", OffsetDateTime.class),
        row.get("rotated_from_credential_id", UUID.class));
  }
}
