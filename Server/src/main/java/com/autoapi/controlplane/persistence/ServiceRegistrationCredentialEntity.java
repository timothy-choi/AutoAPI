package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("service_registration_credentials")
public record ServiceRegistrationCredentialEntity(
    @Id UUID id,
    @Column("service_id") UUID serviceId,
    @Column("credential_id") String credentialId,
    String name,
    @Column("secret_digest") byte[] secretDigest,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    @Column("revoked_at") OffsetDateTime revokedAt)
    implements NewEntity {}
