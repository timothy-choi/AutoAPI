package com.autoapi.controlplane.managementauth;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DelegationValidator {

  private DelegationValidator() {}

  public static void requireCanDelegateRole(
      ManagementPrincipal caller, BuiltInRole roleToGrant, UUID organizationId, UUID projectId) {
    if (caller.principalType() == PrincipalType.SYSTEM
        || caller.principalType() == PrincipalType.BOOTSTRAP_ADMIN) {
      return;
    }
    if (roleToGrant.isOrganizationScoped() && projectId != null) {
      throw com.autoapi.controlplane.api.ControlPlaneException.delegationDenied(
          "Organization roles cannot be granted at project scope");
    }
    if (roleToGrant == BuiltInRole.ORGANIZATION_OWNER
        && !callerHasOrganizationOwner(caller, organizationId)) {
      throw com.autoapi.controlplane.api.ControlPlaneException.delegationDenied(
          "Only organization owners may grant organization owner access");
    }
  }

  public static void requireCanDelegateScopes(
      ManagementPrincipal caller,
      Set<ManagementPermission> requestedScopes,
      UUID organizationId,
      UUID projectId) {
    if (caller.principalType() == PrincipalType.SYSTEM
        || caller.principalType() == PrincipalType.BOOTSTRAP_ADMIN) {
      return;
    }
    Set<ManagementPermission> callerEffective = BuiltInRole.ORGANIZATION_OWNER.permissions();
    if (requestedScopes.stream().anyMatch(scope -> !callerEffective.contains(scope))) {
      throw com.autoapi.controlplane.api.ControlPlaneException.delegationDenied(
          "Cannot delegate scopes beyond caller effective permissions");
    }
    if (caller.credentialScopes() != null && !caller.credentialScopes().isEmpty()) {
      Set<String> allowed = caller.credentialScopes();
      for (ManagementPermission scope : requestedScopes) {
        if (!allowed.contains(scope.value())) {
          throw com.autoapi.controlplane.api.ControlPlaneException.delegationDenied(
              "Cannot delegate scopes beyond credential scope");
        }
      }
    }
  }

  private static boolean callerHasOrganizationOwner(
      ManagementPrincipal caller, UUID organizationId) {
    return caller.roles() != null
        && caller.roles().contains(BuiltInRole.ORGANIZATION_OWNER)
        && organizationId.equals(caller.organizationId());
  }

  public static Set<String> scopeValues(Set<ManagementPermission> permissions) {
    return permissions.stream()
        .map(ManagementPermission::value)
        .collect(Collectors.toUnmodifiableSet());
  }
}
