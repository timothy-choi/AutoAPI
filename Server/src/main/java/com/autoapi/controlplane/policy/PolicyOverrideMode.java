package com.autoapi.controlplane.policy;

/** How a scoped override interacts with inherited policy for a type. */
public enum PolicyOverrideMode {
  INHERIT,
  OVERRIDE,
  MERGE,
  DISABLE,
  APPEND;

  public static PolicyOverrideMode parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("override mode must not be blank");
    }
    return PolicyOverrideMode.valueOf(raw.trim().toUpperCase());
  }
}
