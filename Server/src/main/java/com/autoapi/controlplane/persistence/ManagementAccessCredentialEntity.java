package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("management_access_credentials")
public record ManagementAccessCredentialEntity(
    @Id UUID id,
    @Column("public_token_id") String publicTokenId,
    @Column("principal_type") String principalType,
    @Column("principal_id") UUID principalId,
    @Column("organization_id") UUID organizationId,
    String name,
    String description,
    @Column("secret_digest") byte[] secretDigest,
    @Column("digest_key_version") int digestKeyVersion,
    String scopes,
    String status,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("expires_at") OffsetDateTime expiresAt,
    @Column("last_used_at") OffsetDateTime lastUsedAt,
    @Column("last_used_source") String lastUsedSource,
    @Column("revoked_at") OffsetDateTime revokedAt,
    @Column("rotated_from_credential_id") UUID rotatedFromCredentialId)
    implements NewEntity {}
