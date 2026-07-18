package com.autoapi.config;

import java.util.List;
import java.util.UUID;

public record RuntimeDiscoveredServiceConfig(
    UUID serviceId,
    String selectionStrategy,
    String consistentHashKey,
    String consistentHashKeyName,
    long membershipVersion,
    List<RuntimeDiscoveredInstance> instances) {

  public RuntimeDiscoveredServiceConfig {
    instances = instances == null ? List.of() : List.copyOf(instances);
  }

  public boolean hasEligibleInstances() {
    return !instances.isEmpty();
  }
}
