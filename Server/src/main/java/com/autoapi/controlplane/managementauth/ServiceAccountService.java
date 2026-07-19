package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepository;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepositoryCustom;
import com.autoapi.controlplane.persistence.OrganizationRepository;
import com.autoapi.controlplane.persistence.ServiceAccountEntity;
import com.autoapi.controlplane.persistence.ServiceAccountRepository;
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
public class ServiceAccountService {

  private final ServiceAccountRepository serviceAccountRepository;
  private final OrganizationRepository organizationRepository;
  private final ManagementAuthorizationService authorizationService;
  private final ManagementAccessCredentialRepository credentialRepository;
  private final ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom;
  private final PlatformEventRecorder eventRecorder;

  public ServiceAccountService(
      ServiceAccountRepository serviceAccountRepository,
      OrganizationRepository organizationRepository,
      ManagementAuthorizationService authorizationService,
      ManagementAccessCredentialRepository credentialRepository,
      ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom,
      PlatformEventRecorder eventRecorder) {
    this.serviceAccountRepository = serviceAccountRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationService = authorizationService;
    this.credentialRepository = credentialRepository;
    this.credentialRepositoryCustom = credentialRepositoryCustom;
    this.eventRecorder = eventRecorder;
  }

  public Mono<ServiceAccountEntity> create(
      ManagementPrincipal caller,
      UUID organizationId,
      UUID projectId,
      String name,
      String description,
      EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return authorizationService
        .requireOrganizationPermission(
            caller, organizationId, ManagementPermission.SERVICE_ACCOUNT_MANAGE)
        .then(organizationRepository.findById(organizationId))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Organization was not found")))
        .then(
            Mono.defer(
                () -> {
                  ServiceAccountEntity entity =
                      new ServiceAccountEntity(
                          UUID.randomUUID(),
                          organizationId,
                          projectId,
                          name.trim(),
                          description,
                          "ACTIVE",
                          now,
                          now,
                          null);
                  return serviceAccountRepository
                      .save(entity)
                      .flatMap(
                          saved ->
                              eventRecorder
                                  .record(
                                      RecordPlatformEventRequest.of(
                                          PlatformEventTypes.SERVICE_ACCOUNT_CREATED,
                                          projectId,
                                          null,
                                          "SERVICE_ACCOUNT",
                                          saved.id().toString(),
                                          context,
                                          Map.of(
                                              "name",
                                              saved.name(),
                                              "organizationId",
                                              organizationId.toString())))
                                  .thenReturn(saved));
                }))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Service account name already exists"));
  }

  public Flux<ServiceAccountEntity> list(UUID organizationId, UUID projectId) {
    if (projectId != null) {
      return serviceAccountRepository.findByOrganizationIdAndProjectId(organizationId, projectId);
    }
    return serviceAccountRepository.findByOrganizationId(organizationId);
  }

  public Mono<ServiceAccountEntity> get(UUID organizationId, UUID serviceAccountId) {
    return serviceAccountRepository
        .findByOrganizationIdAndId(organizationId, serviceAccountId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Service account was not found")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<ServiceAccountEntity> disable(
      UUID organizationId, UUID serviceAccountId, EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return get(organizationId, serviceAccountId)
        .flatMap(
            existing -> {
              if ("DISABLED".equals(existing.status())) {
                return Mono.just(existing);
              }
              ServiceAccountEntity disabled =
                  new ServiceAccountEntity(
                      existing.id(),
                      existing.organizationId(),
                      existing.projectId(),
                      existing.name(),
                      existing.description(),
                      "DISABLED",
                      existing.createdAt(),
                      now,
                      now);
              return serviceAccountRepository
                  .save(disabled)
                  .flatMap(
                      saved ->
                          credentialRepository
                              .findByPrincipalTypeAndPrincipalId(
                                  PrincipalType.SERVICE_ACCOUNT.name(), saved.id())
                              .flatMap(
                                  credential ->
                                      credentialRepositoryCustom.revoke(
                                          credential.id(), now, "REVOKED"))
                              .then(
                                  eventRecorder
                                      .record(
                                          RecordPlatformEventRequest.of(
                                              PlatformEventTypes.SERVICE_ACCOUNT_DISABLED,
                                              saved.projectId(),
                                              null,
                                              "SERVICE_ACCOUNT",
                                              saved.id().toString(),
                                              context,
                                              Map.of(
                                                  "name",
                                                  saved.name(),
                                                  "organizationId",
                                                  saved.organizationId().toString())))
                                      .thenReturn(saved)));
            });
  }
}
