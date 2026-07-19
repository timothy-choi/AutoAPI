package com.autoapi.controlplane.managementauth;

import java.util.Set;
import java.util.UUID;

/** Authenticated management-plane identity constructed only after successful authentication. */
public record ManagementPrincipal(
    PrincipalType principalType,
    UUID principalId,
    UUID organizationId,
    String displayName,
    AuthenticationMethod authenticationMethod,
    UUID credentialId,
    Set<String> credentialScopes,
    Set<BuiltInRole> roles,
    String requestId,
    String traceId) {

  public enum AuthenticationMethod {
    MANAGEMENT_TOKEN,
    BOOTSTRAP_TOKEN,
    SYSTEM,
    EXTERNAL_JWT
  }

  public static ManagementPrincipal system(String actorId) {
    return new ManagementPrincipal(
        PrincipalType.SYSTEM,
        UUID.fromString("00000000-0000-0000-0000-000000000000"),
        null,
        actorId,
        AuthenticationMethod.SYSTEM,
        null,
        Set.of(),
        Set.of(),
        null,
        null);
  }

  public boolean hasCredentialScope(ManagementPermission permission) {
    if (credentialScopes == null || credentialScopes.isEmpty()) {
      return true;
    }
    return credentialScopes.contains(permission.value());
  }
}
