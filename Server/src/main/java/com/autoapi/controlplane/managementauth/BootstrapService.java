package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialEntity;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepositoryCustom;
import com.autoapi.controlplane.persistence.ManagementAuthStateEntity;
import com.autoapi.controlplane.persistence.ManagementAuthStateRepository;
import com.autoapi.controlplane.persistence.OrganizationEntity;
import com.autoapi.controlplane.persistence.OrganizationRepository;
import com.autoapi.controlplane.persistence.RoleBindingEntity;
import com.autoapi.controlplane.persistence.RoleBindingRepository;
import com.autoapi.controlplane.persistence.ServiceAccountEntity;
import com.autoapi.controlplane.persistence.ServiceAccountRepository;
import com.autoapi.security.ApiKeyDigestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BootstrapService {

  private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);
  private static final UUID DEFAULT_ORG_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final ManagementAuthStateRepository authStateRepository;
  private final OrganizationRepository organizationRepository;
  private final ServiceAccountRepository serviceAccountRepository;
  private final RoleBindingRepository roleBindingRepository;
  private final ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom;
  private final ManagementAuthProperties properties;
  private final ManagementTokenPepperSource pepperSource;
  private final ManagementAuthorizationService authorizationService;
  private final PlatformEventRecorder eventRecorder;

  public BootstrapService(
      ManagementAuthStateRepository authStateRepository,
      OrganizationRepository organizationRepository,
      ServiceAccountRepository serviceAccountRepository,
      RoleBindingRepository roleBindingRepository,
      ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom,
      ManagementAuthProperties properties,
      ManagementTokenPepperSource pepperSource,
      ManagementAuthorizationService authorizationService,
      PlatformEventRecorder eventRecorder) {
    this.authStateRepository = authStateRepository;
    this.organizationRepository = organizationRepository;
    this.serviceAccountRepository = serviceAccountRepository;
    this.roleBindingRepository = roleBindingRepository;
    this.credentialRepositoryCustom = credentialRepositoryCustom;
    this.properties = properties;
    this.pepperSource = pepperSource;
    this.authorizationService = authorizationService;
    this.eventRecorder = eventRecorder;
  }

  public Mono<Boolean> isInitialized() {
    return authStateRepository
        .findById(1)
        .map(state -> Boolean.TRUE.equals(state.initialized()))
        .defaultIfEmpty(false);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<BootstrapResult> initialize(ManagementPrincipal principal, EventContext context) {
    if (principal.principalType() != PrincipalType.BOOTSTRAP_ADMIN) {
      return Mono.error(ControlPlaneException.forbidden("Bootstrap token is required"));
    }
    return isInitialized()
        .flatMap(
            initialized -> {
              if (initialized && !properties.bootstrap().allowAfterInitialization()) {
                return Mono.error(
                    ControlPlaneException.conflict(
                        "Management authentication is already initialized"));
              }
              return performInitialization(context);
            });
  }

  private Mono<BootstrapResult> performInitialization(EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ManagementTokenPepperValidator.requireConfigured(pepperSource.pepper());
    return organizationRepository
        .findById(DEFAULT_ORG_ID)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.internal("Default organization is missing")))
        .flatMap(
            org -> {
              UUID serviceAccountId = UUID.randomUUID();
              ServiceAccountEntity serviceAccount =
                  new ServiceAccountEntity(
                      serviceAccountId,
                      org.id(),
                      null,
                      "platform-administrator",
                      "Initial bootstrap service account",
                      "ACTIVE",
                      now,
                      now,
                      null);
              UUID bindingId = UUID.randomUUID();
              RoleBindingEntity binding =
                  new RoleBindingEntity(
                      bindingId,
                      org.id(),
                      null,
                      PrincipalType.SERVICE_ACCOUNT.name(),
                      serviceAccountId,
                      BuiltInRole.ORGANIZATION_OWNER.name(),
                      PrincipalType.BOOTSTRAP_ADMIN.name(),
                      principalIdForBootstrap(),
                      now,
                      null,
                      null);
              ManagementTokenGenerator.GeneratedToken tokenMaterial =
                  ManagementTokenGenerator.generate(
                      properties.token().prefix(), properties.token().secretBytes());
              byte[] digest =
                  ApiKeyDigestService.digestSecret(tokenMaterial.secret(), pepperSource.pepper());
              OffsetDateTime expiresAt = now.plus(properties.token().defaultTtl());
              UUID credentialId = UUID.randomUUID();
              ManagementAccessCredentialEntity credential =
                  new ManagementAccessCredentialEntity(
                      credentialId,
                      tokenMaterial.publicTokenId(),
                      PrincipalType.SERVICE_ACCOUNT.name(),
                      serviceAccountId,
                      org.id(),
                      "bootstrap-admin-token",
                      "Initial administrator credential",
                      digest,
                      1,
                      authorizationService.encodeScopes(Set.of()),
                      "ACTIVE",
                      now,
                      expiresAt,
                      null,
                      null,
                      null,
                      null);
              ManagementAuthStateEntity state =
                  new ManagementAuthStateEntity(1, true, org.id(), now);
              return serviceAccountRepository
                  .save(serviceAccount)
                  .then(roleBindingRepository.save(binding))
                  .then(credentialRepositoryCustom.insert(credential))
                  .then(authStateRepository.save(state))
                  .then(
                      eventRecorder.record(
                          RecordPlatformEventRequest.of(
                              PlatformEventTypes.MANAGEMENT_BOOTSTRAP_INITIALIZED,
                              null,
                              null,
                              "ORGANIZATION",
                              org.id().toString(),
                              context,
                              Map.of("serviceAccountId", serviceAccountId.toString()))))
                  .doOnSuccess(
                      ignored ->
                          log.info("bootstrap_admin_initialized organizationId={}", org.id()))
                  .thenReturn(
                      new BootstrapResult(
                          org, serviceAccount, credential, tokenMaterial.plaintextToken()));
            });
  }

  private static UUID principalIdForBootstrap() {
    return UUID.fromString("00000000-0000-0000-0000-000000000002");
  }

  public record BootstrapResult(
      OrganizationEntity organization,
      ServiceAccountEntity serviceAccount,
      ManagementAccessCredentialEntity credential,
      String token) {}
}
