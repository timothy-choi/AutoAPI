package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.persistence.DiscoveredServiceEntity;
import java.util.UUID;

public record DiscoveredServiceResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    boolean enabled,
    String selectionStrategy,
    String registrationMode,
    String defaultScheme,
    int defaultPort,
    String consistentHashKey,
    String consistentHashKeyName,
    long membershipVersion,
    String metadata) {

  public static DiscoveredServiceResponse from(DiscoveredServiceEntity entity) {
    return new DiscoveredServiceResponse(
        entity.id(),
        entity.projectId(),
        entity.name(),
        entity.description(),
        entity.enabled(),
        entity.selectionStrategy(),
        entity.registrationMode(),
        entity.defaultScheme(),
        entity.defaultPort(),
        entity.consistentHashKey(),
        entity.consistentHashKeyName(),
        entity.membershipVersion(),
        entity.metadata());
  }
}
