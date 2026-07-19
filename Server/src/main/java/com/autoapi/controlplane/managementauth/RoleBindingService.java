package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.RoleBindingEntity;
import com.autoapi.controlplane.persistence.RoleBindingRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RoleBindingService {

  private final RoleBindingRepository roleBindingRepository;
  private final ManagementAuthorizationService authorizationService;
  private final PlatformEventRecorder eventRecorder;

  public RoleBindingService(
      RoleBindingRepository roleBindingRepository,
      ManagementAuthorizationService authorizationService,
      PlatformEventRecorder eventRecorder) {
    this.roleBindingRepository = roleBindingRepository;
    this.authorizationService = authorizationService;
    this.eventRecorder = eventRecorder;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RoleBindingEntity> createOrganizationBinding(
      ManagementPrincipal caller,
      UUID organizationId,
      PrincipalType principalType,
      UUID principalId,
      BuiltInRole role,
      OffsetDateTime expiresAt,
      EventContext context) {
    DelegationValidator.requireCanDelegateRole(caller, role, organizationId, null);
    return authorizationService
        .requireOrganizationPermission(
            caller, organizationId, ManagementPermission.ORGANIZATION_MEMBERS_MANAGE)
        .then(
            createBinding(
                caller,
                organizationId,
                null,
                principalType,
                principalId,
                role,
                expiresAt,
                context));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RoleBindingEntity> createProjectBinding(
      ManagementPrincipal caller,
      UUID organizationId,
      UUID projectId,
      PrincipalType principalType,
      UUID principalId,
      BuiltInRole role,
      OffsetDateTime expiresAt,
      EventContext context) {
    DelegationValidator.requireCanDelegateRole(caller, role, organizationId, projectId);
    return authorizationService
        .requireProjectPermission(caller, projectId, ManagementPermission.PROJECT_MEMBERS_MANAGE)
        .then(
            createBinding(
                caller,
                organizationId,
                projectId,
                principalType,
                principalId,
                role,
                expiresAt,
                context));
  }

  private Mono<RoleBindingEntity> createBinding(
      ManagementPrincipal caller,
      UUID organizationId,
      UUID projectId,
      PrincipalType principalType,
      UUID principalId,
      BuiltInRole role,
      OffsetDateTime expiresAt,
      EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    RoleBindingEntity entity =
        new RoleBindingEntity(
            UUID.randomUUID(),
            organizationId,
            projectId,
            principalType.name(),
            principalId,
            role.name(),
            caller.principalType().name(),
            caller.principalId(),
            now,
            expiresAt,
            null);
    return roleBindingRepository
        .save(entity)
        .flatMap(
            saved ->
                eventRecorder
                    .record(
                        RecordPlatformEventRequest.of(
                            PlatformEventTypes.PRINCIPAL_ROLE_BINDING_CREATED,
                            projectId,
                            null,
                            "ROLE_BINDING",
                            saved.id().toString(),
                            context,
                            Map.of(
                                "role",
                                role.name(),
                                "principalType",
                                principalType.name(),
                                "organizationId",
                                organizationId.toString())))
                    .thenReturn(saved))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Active role binding already exists"));
  }

  public Flux<RoleBindingEntity> listOrganizationBindings(UUID organizationId) {
    return roleBindingRepository.findByOrganizationIdAndRevokedAtIsNull(organizationId);
  }

  public Flux<RoleBindingEntity> listProjectBindings(UUID organizationId, UUID projectId) {
    return roleBindingRepository.findByOrganizationIdAndProjectIdAndRevokedAtIsNull(
        organizationId, projectId);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RoleBindingEntity> revoke(
      ManagementPrincipal caller, UUID bindingId, EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return roleBindingRepository
        .findById(bindingId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Role binding was not found")))
        .flatMap(
            existing -> {
              Mono<Void> authorized =
                  existing.projectId() == null
                      ? authorizationService.requireOrganizationPermission(
                          caller,
                          existing.organizationId(),
                          ManagementPermission.ORGANIZATION_MEMBERS_MANAGE)
                      : authorizationService.requireProjectPermission(
                          caller,
                          existing.projectId(),
                          ManagementPermission.PROJECT_MEMBERS_MANAGE);
              return authorized.then(revokeExisting(existing, now, context));
            });
  }

  private Mono<RoleBindingEntity> revokeExisting(
      RoleBindingEntity existing, OffsetDateTime now, EventContext context) {
    if (existing.revokedAt() != null) {
      return Mono.just(existing);
    }
    RoleBindingEntity revoked =
        new RoleBindingEntity(
            existing.id(),
            existing.organizationId(),
            existing.projectId(),
            existing.principalType(),
            existing.principalId(),
            existing.role(),
            existing.createdByPrincipalType(),
            existing.createdByPrincipalId(),
            existing.createdAt(),
            existing.expiresAt(),
            now);
    return roleBindingRepository
        .save(revoked)
        .flatMap(
            saved ->
                eventRecorder
                    .record(
                        RecordPlatformEventRequest.of(
                            PlatformEventTypes.PRINCIPAL_ROLE_BINDING_REVOKED,
                            saved.projectId(),
                            null,
                            "ROLE_BINDING",
                            saved.id().toString(),
                            context,
                            Map.of(
                                "role",
                                saved.role(),
                                "organizationId",
                                saved.organizationId().toString())))
                    .thenReturn(saved));
  }
}
