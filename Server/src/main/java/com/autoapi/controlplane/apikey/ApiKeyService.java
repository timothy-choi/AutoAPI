package com.autoapi.controlplane.apikey;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.ApiKeyRepository;
import com.autoapi.controlplane.persistence.ApiKeyRepositoryCustom;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.security.ApiKeyPepperProperties;
import com.autoapi.security.ApiKeyPepperValidator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class ApiKeyService {

  private final ApiKeyRepository apiKeyRepository;
  private final ApiKeyRepositoryCustom apiKeyRepositoryCustom;
  private final ApiDefinitionService apiDefinitionService;
  private final ApiKeyPepperProperties pepperProperties;

  public ApiKeyService(
      ApiKeyRepository apiKeyRepository,
      ApiKeyRepositoryCustom apiKeyRepositoryCustom,
      ApiDefinitionService apiDefinitionService,
      ApiKeyPepperProperties pepperProperties) {
    this.apiKeyRepository = apiKeyRepository;
    this.apiKeyRepositoryCustom = apiKeyRepositoryCustom;
    this.apiDefinitionService = apiDefinitionService;
    this.pepperProperties = pepperProperties;
  }

  public Mono<CreatedApiKey> create(UUID apiId, String name, OffsetDateTime expiresAt) {
    ApiKeyPepperValidator.requireConfigured(pepperProperties.apiKeyPepper());
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
      return Mono.error(ControlPlaneException.invalidRequest("expiresAt must be in the future"));
    }
    ApiKeyGenerator.GeneratedApiKeyMaterial material = ApiKeyGenerator.generate();
    byte[] digest =
        ApiKeyDigestService.digestSecret(material.secret(), pepperProperties.apiKeyPepper());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ApiKeyEntity entity =
        new ApiKeyEntity(
            UUID.randomUUID(),
            apiId,
            material.keyId(),
            name.trim(),
            material.keyPrefix(),
            digest,
            true,
            expiresAt,
            now,
            now,
            null);
    return apiDefinitionService
        .get(apiId)
        .then(apiKeyRepository.save(entity))
        .map(saved -> CreatedApiKey.from(saved, material.plaintextKey()))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict(
                        "An API key with the same name or keyId already exists")));
  }

  public Flux<ApiKeyEntity> list(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(apiKeyRepository.findByApiId(apiId));
  }

  public Mono<ApiKeyEntity> get(UUID apiId, String keyId) {
    return apiDefinitionService
        .get(apiId)
        .then(apiKeyRepository.findByApiIdAndKeyId(apiId, keyId))
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API key was not found")));
  }

  public Mono<ApiKeyEntity> revoke(UUID apiId, String keyId) {
    return get(apiId, keyId)
        .flatMap(
            existing -> {
              if (existing.revokedAt() != null) {
                return Mono.just(existing);
              }
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return apiKeyRepositoryCustom.revoke(existing.id(), now, now);
            });
  }

  public record CreatedApiKey(
      UUID id,
      UUID apiId,
      String keyId,
      String name,
      String keyPrefix,
      String plaintextKey,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {

    static CreatedApiKey from(ApiKeyEntity entity, String plaintextKey) {
      return new CreatedApiKey(
          entity.id(),
          entity.apiId(),
          entity.keyId(),
          entity.name(),
          entity.keyPrefix(),
          plaintextKey,
          entity.expiresAt(),
          entity.createdAt());
    }
  }
}
