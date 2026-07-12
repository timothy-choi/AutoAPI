package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("api_keys")
public record ApiKeyEntity(
    @Id UUID id,
    @Column("api_id") UUID apiId,
    @Column("key_id") String keyId,
    String name,
    @Column("key_prefix") String keyPrefix,
    @Column("secret_digest") byte[] secretDigest,
    boolean enabled,
    @Column("expires_at") OffsetDateTime expiresAt,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("revoked_at") OffsetDateTime revokedAt)
    implements NewEntity {}
