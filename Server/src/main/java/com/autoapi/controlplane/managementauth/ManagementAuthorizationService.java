package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.OrganizationRepository;
import com.autoapi.controlplane.persistence.ProjectEntity;
import com.autoapi.controlplane.persistence.ProjectRepository;
import com.autoapi.controlplane.persistence.RoleBindingEntity;
import com.autoapi.controlplane.persistence.RoleBindingRepository;
import com.autoapi.controlplane.persistence.ServiceAccountEntity;
import com.autoapi.controlplane.persistence.ServiceAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ManagementAuthorizationService {

  private final RoleBindingRepository roleBindingRepository;
  private final OrganizationRepository organizationRepository;
  private final ProjectRepository projectRepository;
  private final ServiceAccountRepository serviceAccountRepository;
  private final ObjectMapper objectMapper;
  private final ManagementAuthMetrics metrics;

  public ManagementAuthorizationService(
      RoleBindingRepository roleBindingRepository,
      OrganizationRepository organizationRepository,
      ProjectRepository projectRepository,
      ServiceAccountRepository serviceAccountRepository,
      ObjectMapper objectMapper,
      ManagementAuthMetrics metrics) {
    this.roleBindingRepository = roleBindingRepository;
    this.organizationRepository = organizationRepository;
    this.projectRepository = projectRepository;
    this.serviceAccountRepository = serviceAccountRepository;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  public Mono<Set<BuiltInRole>> resolveRoles(
      PrincipalType principalType, UUID principalId, UUID organizationId, UUID projectId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Flux<RoleBindingEntity> orgBindings =
        roleBindingRepository
            .findByOrganizationIdAndRevokedAtIsNull(organizationId)
            .filter(binding -> binding.projectId() == null);
    Flux<RoleBindingEntity> projectBindings =
        projectId == null
            ? Flux.empty()
            : roleBindingRepository.findByOrganizationIdAndProjectIdAndRevokedAtIsNull(
                organizationId, projectId);
    return Flux.merge(orgBindings, projectBindings)
        .filter(binding -> binding.principalType().equals(principalType.name()))
        .filter(binding -> binding.principalId().equals(principalId))
        .filter(binding -> binding.expiresAt() == null || binding.expiresAt().isAfter(now))
        .map(binding -> BuiltInRole.parse(binding.role()))
        .collect(Collectors.toSet());
  }

  public Mono<Set<ManagementPermission>> effectivePermissions(
      ManagementPrincipal principal, UUID organizationId, UUID projectId) {
    if (principal.principalType() == PrincipalType.SYSTEM) {
      return Mono.just(EnumSet.allOf(ManagementPermission.class));
    }
    if (principal.principalType() == PrincipalType.BOOTSTRAP_ADMIN) {
      return Mono.just(permissionsFromRoles(principal.roles(), principal.credentialScopes()));
    }
    return resolveRoles(
            principal.principalType(), principal.principalId(), organizationId, projectId)
        .map(roles -> permissionsFromRoles(roles, principal.credentialScopes()));
  }

  private static Set<ManagementPermission> permissionsFromRoles(
      Set<BuiltInRole> roles, Set<String> credentialScopes) {
    Set<ManagementPermission> permissions = new HashSet<>();
    for (BuiltInRole role : roles) {
      permissions.addAll(role.permissions());
    }
    if (credentialScopes != null && !credentialScopes.isEmpty()) {
      permissions.removeIf(permission -> !credentialScopes.contains(permission.value()));
    }
    return Set.copyOf(permissions);
  }

  public Mono<Void> requireOrganizationPermission(
      ManagementPrincipal principal, UUID organizationId, ManagementPermission permission) {
    return ensureOrganizationAccessible(organizationId)
        .then(effectivePermissions(principal, organizationId, null))
        .flatMap(
            permissions -> {
              if (permissions.contains(permission)) {
                metrics.recordAuthorizationDecision(true, permission);
                return Mono.empty();
              }
              metrics.recordAuthorizationDecision(false, permission);
              return Mono.error(
                  ControlPlaneException.forbidden("Permission denied: " + permission.value()));
            });
  }

  public Mono<Void> requireProjectPermission(
      ManagementPrincipal principal, UUID projectId, ManagementPermission permission) {
    return projectRepository
        .findById(projectId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.resourceNotAccessible("Project was not found")))
        .flatMap(
            project ->
                ensureOrganizationAccessible(project.organizationId())
                    .then(effectivePermissions(principal, project.organizationId(), projectId))
                    .flatMap(
                        permissions -> {
                          if (permissions.contains(permission)) {
                            metrics.recordAuthorizationDecision(true, permission);
                            return Mono.empty();
                          }
                          metrics.recordAuthorizationDecision(false, permission);
                          return Mono.error(
                              ControlPlaneException.forbidden(
                                  "Permission denied: " + permission.value()));
                        }));
  }

  public Mono<ProjectEntity> requireProjectRead(ManagementPrincipal principal, UUID projectId) {
    return requireProjectPermission(principal, projectId, ManagementPermission.PROJECT_READ)
        .then(projectRepository.findById(projectId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.resourceNotAccessible("Project was not found")));
  }

  public Mono<Void> requireCanDelegateScopes(
      ManagementPrincipal caller,
      UUID organizationId,
      UUID projectId,
      Set<ManagementPermission> requestedScopes) {
    return effectivePermissions(caller, organizationId, projectId)
        .flatMap(
            effective -> {
              for (ManagementPermission scope : requestedScopes) {
                if (!effective.contains(scope)) {
                  return Mono.error(
                      ControlPlaneException.delegationDenied(
                          "Cannot delegate scope: " + scope.value()));
                }
              }
              return Mono.empty();
            });
  }

  public Mono<Void> ensureOrganizationAccessible(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.resourceNotAccessible("Organization was not found")))
        .flatMap(
            org -> {
              if (!"ACTIVE".equals(org.status())) {
                return Mono.error(ControlPlaneException.forbidden("Organization is not active"));
              }
              return Mono.empty();
            });
  }

  public Mono<Void> ensureServiceAccountActive(ServiceAccountEntity account) {
    if (!"ACTIVE".equals(account.status())) {
      return Mono.error(ControlPlaneException.principalDisabled("Service account is disabled"));
    }
    return Mono.empty();
  }

  public Set<String> decodeScopes(String scopesJson) {
    return ManagementScopeCodec.decode(scopesJson, objectMapper);
  }

  public Set<ManagementPermission> decodePermissions(String scopesJson) {
    return ManagementScopeCodec.decodePermissions(scopesJson, objectMapper);
  }

  public String encodeScopes(Set<String> scopes) {
    return ManagementScopeCodec.encode(scopes, objectMapper);
  }
}
