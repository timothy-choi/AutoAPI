package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.ServiceRegistrationCredentialEntity;
import com.autoapi.controlplane.persistence.ServiceRegistrationCredentialRepository;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.security.ApiKeyPepperProperties;
import com.autoapi.security.ApiKeyPepperValidator;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ServiceRegistrationCredentialService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int SECRET_BYTES = 32;

  private final ServiceRegistrationCredentialRepository repository;
  private final DiscoveredServiceService discoveredServiceService;
  private final ApiKeyPepperProperties pepperProperties;

  public ServiceRegistrationCredentialService(
      ServiceRegistrationCredentialRepository repository,
      DiscoveredServiceService discoveredServiceService,
      ApiKeyPepperProperties pepperProperties) {
    this.repository = repository;
    this.discoveredServiceService = discoveredServiceService;
    this.pepperProperties = pepperProperties;
  }

  public Mono<CreatedRegistrationCredential> create(UUID projectId, UUID serviceId, String name) {
    ApiKeyPepperValidator.requireConfigured(pepperProperties.apiKeyPepper());
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    GeneratedCredential material = generate();
    byte[] digest =
        ApiKeyDigestService.digestSecret(material.secret(), pepperProperties.apiKeyPepper());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ServiceRegistrationCredentialEntity entity =
        new ServiceRegistrationCredentialEntity(
            UUID.randomUUID(),
            serviceId,
            material.credentialId(),
            name.trim(),
            digest,
            true,
            now,
            now,
            null);
    return discoveredServiceService
        .get(projectId, serviceId)
        .then(repository.save(entity))
        .map(saved -> CreatedRegistrationCredential.from(saved, material.plaintextToken()))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex ->
                ControlPlaneException.conflict(
                    "A registration credential with the same name or id already exists"));
  }

  public Flux<ServiceRegistrationCredentialEntity> list(UUID projectId, UUID serviceId) {
    return discoveredServiceService
        .get(projectId, serviceId)
        .thenMany(repository.findByServiceId(serviceId));
  }

  public Mono<Void> validateToken(UUID serviceId, String plaintextToken) {
    ApiKeyPepperValidator.requireConfigured(pepperProperties.apiKeyPepper());
    ParsedToken parsed = parseToken(plaintextToken);
    byte[] digest =
        ApiKeyDigestService.digestSecret(parsed.secret(), pepperProperties.apiKeyPepper());
    return repository
        .findByServiceIdAndCredentialId(serviceId, parsed.credentialId())
        .switchIfEmpty(Mono.error(ControlPlaneException.unauthorized("Invalid registration token")))
        .flatMap(
            credential -> {
              if (!credential.enabled() || credential.revokedAt() != null) {
                return Mono.error(
                    ControlPlaneException.unauthorized("Registration credential is revoked"));
              }
              if (!ApiKeyDigestService.constantTimeEquals(credential.secretDigest(), digest)) {
                return Mono.error(ControlPlaneException.unauthorized("Invalid registration token"));
              }
              return Mono.empty();
            });
  }

  private static ParsedToken parseToken(String plaintextToken) {
    if (plaintextToken == null || !plaintextToken.startsWith("sr_live_")) {
      throw ControlPlaneException.unauthorized("Invalid registration token format");
    }
    String remainder = plaintextToken.substring("sr_live_".length());
    int dot = remainder.indexOf('.');
    if (dot <= 0 || dot >= remainder.length() - 1) {
      throw ControlPlaneException.unauthorized("Invalid registration token format");
    }
    return new ParsedToken(remainder.substring(0, dot), remainder.substring(dot + 1));
  }

  private static GeneratedCredential generate() {
    String credentialId = ApiKeyGenerator.generate().keyId();
    byte[] secretBytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(secretBytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    String plaintextToken = "sr_live_" + credentialId + "." + secret;
    return new GeneratedCredential(credentialId, secret, plaintextToken);
  }

  private record ParsedToken(String credentialId, String secret) {}

  private record GeneratedCredential(String credentialId, String secret, String plaintextToken) {}

  public record CreatedRegistrationCredential(
      UUID id,
      UUID serviceId,
      String credentialId,
      String name,
      boolean enabled,
      String plaintextToken) {

    static CreatedRegistrationCredential from(
        ServiceRegistrationCredentialEntity entity, String plaintextToken) {
      return new CreatedRegistrationCredential(
          entity.id(),
          entity.serviceId(),
          entity.credentialId(),
          entity.name(),
          entity.enabled(),
          plaintextToken);
    }
  }
}
