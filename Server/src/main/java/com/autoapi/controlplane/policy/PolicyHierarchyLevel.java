package com.autoapi.controlplane.policy;

/** Ordered policy inheritance levels from broadest to most specific. */
public enum PolicyHierarchyLevel {
  ORGANIZATION(0),
  PROJECT(1),
  GATEWAY_GROUP(2),
  API(3),
  ROUTE(4);

  private final int order;

  PolicyHierarchyLevel(int order) {
    this.order = order;
  }

  public int order() {
    return order;
  }

  public boolean isBefore(PolicyHierarchyLevel other) {
    return this.order < other.order;
  }

  public static PolicyHierarchyLevel parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("scope level must not be blank");
    }
    return PolicyHierarchyLevel.valueOf(raw.trim().toUpperCase());
  }
}
