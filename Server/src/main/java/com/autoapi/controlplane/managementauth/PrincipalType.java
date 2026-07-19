package com.autoapi.controlplane.managementauth;

public enum PrincipalType {
  USER,
  SERVICE_ACCOUNT,
  BOOTSTRAP_ADMIN,
  SYSTEM;

  public String actorType() {
    return switch (this) {
      case USER -> "USER";
      case SERVICE_ACCOUNT -> "SERVICE_ACCOUNT";
      case BOOTSTRAP_ADMIN -> "BOOTSTRAP_ADMIN";
      case SYSTEM -> "SYSTEM";
    };
  }

  public static PrincipalType parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Principal type must not be blank");
    }
    return PrincipalType.valueOf(raw.trim().toUpperCase());
  }
}
