package com.autoapi.runtime;

public enum AutoApiRole {
  CONTROL_PLANE,
  GATEWAY,
  COMBINED;

  public boolean runsControlPlane() {
    return this == CONTROL_PLANE || this == COMBINED;
  }

  public boolean runsGateway() {
    return this == GATEWAY || this == COMBINED;
  }

  public static AutoApiRole parse(String value) {
    if (value == null || value.isBlank()) {
      return COMBINED;
    }
    return AutoApiRole.valueOf(value.trim().toUpperCase().replace('-', '_'));
  }
}
