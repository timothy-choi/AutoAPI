package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GatewayGroupEntity(
    UUID id,
    UUID projectId,
    UUID apiId,
    String name,
    String description,
    String region,
    String zone,
    String environment,
    String selectorJson,
    boolean enabled,
    Long desiredConfigVersion,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt,
    long version) {}
