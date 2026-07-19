package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialEntity;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepository;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepositoryCustom;
import com.autoapi.controlplane.persistence.ServiceAccountEntity;
import com.autoapi.controlplane.persistence.ServiceAccountRepository;
import com.autoapi.security.ApiKeyDigestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
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
public class ManagementCredentialService {

  private final ManagementAccessCredentialRepository credentialRepository;
  private final ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom;
  private final ServiceAccountRepository serviceAccountRepository;
  private final ManagementAuthorizationService authorizationService;
  private final ManagementAuthProperties properties;
  private final ManagementTokenPepperSource pepperSource;
  private final PlatformEventRecorder eventRecorder;
  private final ManagementAuthMetrics metrics;

  public ManagementCredentialService(
      ManagementAccessCredentialRepository credentialRepository,
      ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom,
      ServiceAccountRepository serviceAccountRepository,
      ManagementAuthorizationService authorizationService,
      ManagementAuthProperties properties,
      ManagementTokenPepperSource pepperSource,
      PlatformEventRecorder eventRecorder,
      ManagementAuthMetrics metrics) {
    this.credentialRepository = credentialRepository;
    this.credentialRepositoryCustom = credentialRepositoryCustom;
    this.serviceAccountRepository = serviceAccountRepository;
    this.authorizationService = authorizationService;
    this.properties = properties;
    this.pepperSource = pepperSource;
    this.eventRecorder = eventRecorder;
    this.metrics = metrics;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<CreatedCredential> create(
      ManagementPrincipal caller,
      UUID serviceAccountId,
      String name,
      String description,
      Set<ManagementPermission> scopes,
      OffsetDateTime expiresAt,
      EventContext context) {
    ManagementTokenPepperValidator.requireConfigured(pepperSource.pepper());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime effectiveExpiresAt =
        expiresAt == null ? now.plus(properties.token().defaultTtl()) : expiresAt;
    if (effectiveExpiresAt.isAfter(now.plus(properties.token().maxTtl()))) {
      return Mono.error(
          ControlPlaneException.invalidRequest("expiresAt exceeds maximum credential TTL"));
    }
    final OffsetDateTime credentialExpiresAt = effectiveExpiresAt;
    return serviceAccountRepository
        .findById(serviceAccountId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Service account was not found")))
        .flatMap(
            account ->
                authorizationService
                    .ensureServiceAccountActive(account)
                    .then(
                        authorizationService.requireCanDelegateScopes(
                            caller, account.organizationId(), account.projectId(), scopes))
                    .then(
                        createCredential(
                            account,
                            caller,
                            name,
                            description,
                            scopes,
                            credentialExpiresAt,
                            now,
                            context)));
  }

  private Mono<CreatedCredential> createCredential(
      ServiceAccountEntity account,
      ManagementPrincipal caller,
      String name,
      String description,
      Set<ManagementPermission> scopes,
      OffsetDateTime expiresAt,
      OffsetDateTime now,
      EventContext context) {
    ManagementTokenGenerator.GeneratedToken material =
        ManagementTokenGenerator.generate(
            properties.token().prefix(), properties.token().secretBytes());
    byte[] digest = ApiKeyDigestService.digestSecret(material.secret(), pepperSource.pepper());
    UUID credentialId = UUID.randomUUID();
    ManagementAccessCredentialEntity entity =
        new ManagementAccessCredentialEntity(
            credentialId,
            material.publicTokenId(),
            PrincipalType.SERVICE_ACCOUNT.name(),
            account.id(),
            account.organizationId(),
            name == null ? "credential" : name.trim(),
            description,
            digest,
            1,
            authorizationService.encodeScopes(DelegationValidator.scopeValues(scopes)),
            "ACTIVE",
            now,
            expiresAt,
            null,
            null,
            null,
            null);
    return credentialRepositoryCustom
        .insert(entity)
        .flatMap(
            saved ->
                eventRecorder
                    .record(
                        RecordPlatformEventRequest.of(
                            PlatformEventTypes.MANAGEMENT_CREDENTIAL_CREATED,
                            account.projectId(),
                            null,
                            "MANAGEMENT_CREDENTIAL",
                            saved.id().toString(),
                            context,
                            Map.of(
                                "serviceAccountId",
                                account.id().toString(),
                                "name",
                                saved.name(),
                                "organizationId",
                                account.organizationId().toString())))
                    .thenReturn(new CreatedCredential(saved, material.plaintextToken())))
        .doOnSuccess(ignored -> metrics.recordCredentialCreated())
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Credential public token id conflict"));
  }

  public Flux<ManagementAccessCredentialEntity> list(
      ManagementPrincipal caller, UUID serviceAccountId) {
    return requireServiceAccountPermission(
            caller, serviceAccountId, ManagementPermission.CREDENTIAL_READ)
        .flatMapMany(
            account ->
                credentialRepository.findByPrincipalTypeAndPrincipalId(
                    PrincipalType.SERVICE_ACCOUNT.name(), account.id()));
  }

  public Mono<ManagementAccessCredentialEntity> get(
      ManagementPrincipal caller, UUID serviceAccountId, UUID credentialId) {
    return requireServiceAccountPermission(
            caller, serviceAccountId, ManagementPermission.CREDENTIAL_READ)
        .then(getCredential(serviceAccountId, credentialId));
  }

  private Mono<ManagementAccessCredentialEntity> getCredential(
      UUID serviceAccountId, UUID credentialId) {
    return credentialRepository
        .findById(credentialId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Credential was not found")))
        .flatMap(
            credential -> {
              if (!credential.principalId().equals(serviceAccountId)) {
                return Mono.error(ControlPlaneException.notFound("Credential was not found"));
              }
              return Mono.just(credential);
            });
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<CreatedCredential> rotate(
      ManagementPrincipal caller, UUID serviceAccountId, UUID credentialId, EventContext context) {
    return requireServiceAccountPermission(
            caller, serviceAccountId, ManagementPermission.CREDENTIAL_ROTATE)
        .then(getCredential(serviceAccountId, credentialId))
        .flatMap(
            existing -> {
              Set<ManagementPermission> scopes =
                  authorizationService.decodePermissions(existing.scopes());
              return credentialRepositoryCustom
                  .markRotated(existing.id(), OffsetDateTime.now(ZoneOffset.UTC))
                  .then(
                      create(
                          caller,
                          serviceAccountId,
                          existing.name() + "-rotated",
                          existing.description(),
                          scopes,
                          existing.expiresAt(),
                          context))
                  .doOnSuccess(ignored -> metrics.recordCredentialRotated());
            });
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<ManagementAccessCredentialEntity> revoke(
      ManagementPrincipal caller, UUID serviceAccountId, UUID credentialId, EventContext context) {
    return requireServiceAccountPermission(
            caller, serviceAccountId, ManagementPermission.CREDENTIAL_REVOKE)
        .then(getCredential(serviceAccountId, credentialId))
        .flatMap(
            existing ->
                credentialRepositoryCustom
                    .revoke(existing.id(), OffsetDateTime.now(ZoneOffset.UTC), "REVOKED")
                    .flatMap(
                        revoked ->
                            eventRecorder
                                .record(
                                    RecordPlatformEventRequest.of(
                                        PlatformEventTypes.MANAGEMENT_CREDENTIAL_REVOKED,
                                        null,
                                        null,
                                        "MANAGEMENT_CREDENTIAL",
                                        revoked.id().toString(),
                                        context,
                                        Map.of(
                                            "serviceAccountId",
                                            serviceAccountId.toString(),
                                            "organizationId",
                                            revoked.organizationId().toString())))
                                .thenReturn(revoked)))
        .doOnSuccess(ignored -> metrics.recordCredentialRevoked());
  }

  private Mono<ServiceAccountEntity> requireServiceAccountPermission(
      ManagementPrincipal caller, UUID serviceAccountId, ManagementPermission permission) {
    return serviceAccountRepository
        .findById(serviceAccountId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Service account was not found")))
        .flatMap(
            account ->
                authorizationService
                    .ensureServiceAccountActive(account)
                    .then(
                        account.projectId() == null
                            ? authorizationService.requireOrganizationPermission(
                                caller, account.organizationId(), permission)
                            : authorizationService.requireProjectPermission(
                                caller, account.projectId(), permission))
                    .thenReturn(account));
  }

  public record CreatedCredential(ManagementAccessCredentialEntity credential, String token) {}
}
