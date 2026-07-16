package com.autoapi.config;

import java.util.List;
import java.util.UUID;

public record RuntimeTrafficSplitConfig(
    UUID policyId,
    String selectionKey,
    String selectionKeyName,
    String fallbackMode,
    String fingerprint,
    int totalWeight,
    List<RuntimeTrafficSplitDestination> destinations) {

  public RuntimeTrafficSplitConfig {
    destinations = List.copyOf(destinations);
  }
}
