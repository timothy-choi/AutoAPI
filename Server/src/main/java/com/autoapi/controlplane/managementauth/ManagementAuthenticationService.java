package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialEntity;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepository;
import com.autoapi.controlplane.persistence.ManagementAccessCredentialRepositoryCustom;
import com.autoapi.controlplane.persistence.ManagementUserRepository;
import com.autoapi.controlplane.persistence.OrganizationEntity;
import com.autoapi.controlplane.persistence.OrganizationRepository;
import com.autoapi.controlplane.persistence.ServiceAccountRepository;
import com.autoapi.security.ApiKeyDigestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ManagementAuthenticationService {

  private final ManagementAccessCredentialRepository credentialRepository;
  private final ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom;
  private final ManagementUserRepository managementUserRepository;
  private final ServiceAccountRepository serviceAccountRepository;
  private final OrganizationRepository organizationRepository;
  private final ManagementAuthorizationService authorizationService;
  private final ManagementAuthProperties properties;
  private final ManagementTokenPepperSource pepperSource;
  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final ManagementAuthMetrics metrics;

  public ManagementAuthenticationService(
      ManagementAccessCredentialRepository credentialRepository,
      ManagementAccessCredentialRepositoryCustom credentialRepositoryCustom,
      ManagementUserRepository managementUserRepository,
      ServiceAccountRepository serviceAccountRepository,
      OrganizationRepository organizationRepository,
      ManagementAuthorizationService authorizationService,
      ManagementAuthProperties properties,
      ManagementTokenPepperSource pepperSource,
      Environment environment,
      ObjectMapper objectMapper,
      ManagementAuthMetrics metrics) {
    this.credentialRepository = credentialRepository;
    this.credentialRepositoryCustom = credentialRepositoryCustom;
    this.managementUserRepository = managementUserRepository;
    this.serviceAccountRepository = serviceAccountRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationService = authorizationService;
    this.properties = properties;
    this.pepperSource = pepperSource;
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  public Mono<ManagementPrincipal> authenticateBearerToken(String bearerToken, String requestId) {
    if (bearerToken == null || bearerToken.isBlank()) {
      return Mono.error(ControlPlaneException.authenticationRequired("Bearer token is required"));
    }
    if (isBootstrapToken(bearerToken)) {
      return authenticateBootstrapToken(bearerToken, requestId);
    }
    ManagementTokenParser.ParsedToken parsed;
    try {
      parsed = ManagementTokenParser.parse(bearerToken, properties.token().prefix());
    } catch (ControlPlaneException ex) {
      metrics.recordAuthenticationFailure("invalid_format");
      return Mono.error(ex);
    }
    String pepper = pepperSource.pepper();
    byte[] digest = ApiKeyDigestService.digestSecret(parsed.secret(), pepper);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return credentialRepository
        .findByPublicTokenId(parsed.publicTokenId())
        .switchIfEmpty(
            Mono.fromRunnable(() -> metrics.recordAuthenticationFailure("unknown_credential"))
                .then(Mono.error(ControlPlaneException.invalidCredential("Invalid credential"))))
        .flatMap(credential -> validateCredential(credential, digest, now))
        .flatMap(credential -> resolvePrincipal(credential, requestId))
        .doOnSuccess(principal -> metrics.recordAuthenticationSuccess(principal))
        .doOnError(
            ControlPlaneException.class,
            ex -> {
              if (!"INVALID_CREDENTIAL".equals(ex.code())
                  && !"CREDENTIAL_EXPIRED".equals(ex.code())
                  && !"CREDENTIAL_REVOKED".equals(ex.code())) {
                return;
              }
              metrics.recordAuthenticationFailure(ex.code().toLowerCase());
            });
  }

  private Mono<ManagementAccessCredentialEntity> validateCredential(
      ManagementAccessCredentialEntity credential, byte[] digest, OffsetDateTime now) {
    if (!"ACTIVE".equals(credential.status())) {
      if ("REVOKED".equals(credential.status()) || credential.revokedAt() != null) {
        return Mono.error(ControlPlaneException.credentialRevoked("Credential is revoked"));
      }
      if ("EXPIRED".equals(credential.status())) {
        return Mono.error(ControlPlaneException.credentialExpired("Credential is expired"));
      }
      return Mono.error(ControlPlaneException.invalidCredential("Invalid credential"));
    }
    if (credential.expiresAt() != null && !credential.expiresAt().isAfter(now)) {
      return Mono.error(ControlPlaneException.credentialExpired("Credential is expired"));
    }
    if (!ApiKeyDigestService.constantTimeEquals(credential.secretDigest(), digest)) {
      return Mono.error(ControlPlaneException.invalidCredential("Invalid credential"));
    }
    return Mono.just(credential);
  }

  private Mono<ManagementPrincipal> resolvePrincipal(
      ManagementAccessCredentialEntity credential, String requestId) {
    PrincipalType principalType = PrincipalType.parse(credential.principalType());
    Set<String> scopes = authorizationService.decodeScopes(credential.scopes());
    return organizationRepository
        .findById(credential.organizationId())
        .switchIfEmpty(Mono.error(ControlPlaneException.invalidCredential("Invalid credential")))
        .flatMap(
            org ->
                ensureOrganizationActive(org)
                    .then(resolvePrincipalDetails(principalType, credential))
                    .flatMap(
                        details ->
                            authorizationService
                                .resolveRoles(
                                    principalType,
                                    credential.principalId(),
                                    credential.organizationId(),
                                    details.projectId())
                                .map(
                                    roles ->
                                        new ManagementPrincipal(
                                            principalType,
                                            credential.principalId(),
                                            credential.organizationId(),
                                            details.displayName(),
                                            ManagementPrincipal.AuthenticationMethod
                                                .MANAGEMENT_TOKEN,
                                            credential.id(),
                                            scopes,
                                            roles,
                                            requestId,
                                            null)))
                    .flatMap(
                        principal ->
                            touchLastUsed(credential)
                                .onErrorResume(ex -> Mono.empty())
                                .thenReturn(principal)));
  }

  private Mono<Void> ensureOrganizationActive(OrganizationEntity organization) {
    if (!"ACTIVE".equals(organization.status())) {
      return Mono.error(ControlPlaneException.forbidden("Organization is suspended"));
    }
    return Mono.empty();
  }

  private record PrincipalDetails(String displayName, UUID projectId) {}

  private Mono<PrincipalDetails> resolvePrincipalDetails(
      PrincipalType principalType, ManagementAccessCredentialEntity credential) {
    return switch (principalType) {
      case USER ->
          managementUserRepository
              .findById(credential.principalId())
              .switchIfEmpty(
                  Mono.error(ControlPlaneException.invalidCredential("Invalid credential")))
              .flatMap(
                  user -> {
                    if (!"ACTIVE".equals(user.status())) {
                      return Mono.error(
                          ControlPlaneException.principalDisabled("User is disabled"));
                    }
                    return Mono.just(new PrincipalDetails(user.displayName(), null));
                  });
      case SERVICE_ACCOUNT ->
          serviceAccountRepository
              .findById(credential.principalId())
              .switchIfEmpty(
                  Mono.error(ControlPlaneException.invalidCredential("Invalid credential")))
              .flatMap(
                  account -> {
                    if (!"ACTIVE".equals(account.status())) {
                      return Mono.error(
                          ControlPlaneException.principalDisabled("Service account is disabled"));
                    }
                    return Mono.just(new PrincipalDetails(account.name(), account.projectId()));
                  });
      case BOOTSTRAP_ADMIN -> Mono.just(new PrincipalDetails("Bootstrap Administrator", null));
      default -> Mono.error(ControlPlaneException.invalidCredential("Invalid credential"));
    };
  }

  private Mono<Void> touchLastUsed(ManagementAccessCredentialEntity credential) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleBefore = now.minus(properties.token().lastUsedUpdateInterval());
    return credentialRepositoryCustom
        .touchLastUsedIfStale(credential.id(), now, staleBefore, "management-api")
        .then();
  }

  private boolean isBootstrapToken(String token) {
    String configured = resolveBootstrapToken();
    return properties.bootstrap().enabled()
        && configured != null
        && !configured.isBlank()
        && configured.equals(token);
  }

  private String resolveBootstrapToken() {
    String configured = properties.bootstrap().token();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    String fromEnv = environment.getProperty("AUTOAPI_BOOTSTRAP_ADMIN_TOKEN");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return environment.getProperty("autoapi.management-auth.bootstrap.token", "");
  }

  private Mono<ManagementPrincipal> authenticateBootstrapToken(String token, String requestId) {
    UUID bootstrapPrincipalId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID defaultOrgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    return Mono.just(
        new ManagementPrincipal(
            PrincipalType.BOOTSTRAP_ADMIN,
            bootstrapPrincipalId,
            defaultOrgId,
            "Bootstrap Administrator",
            ManagementPrincipal.AuthenticationMethod.BOOTSTRAP_TOKEN,
            null,
            Set.of(),
            Set.of(BuiltInRole.ORGANIZATION_OWNER),
            requestId,
            null));
  }
}
