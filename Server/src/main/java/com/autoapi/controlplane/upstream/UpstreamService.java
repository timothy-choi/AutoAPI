package com.autoapi.controlplane.upstream;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolRepository;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetRepository;
import com.autoapi.validation.UpstreamUriValidator;
import java.net.URI;
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
public class UpstreamService {

  private final UpstreamPoolRepository upstreamPoolRepository;
  private final UpstreamTargetRepository upstreamTargetRepository;
  private final ApiDefinitionService apiDefinitionService;

  public UpstreamService(
      UpstreamPoolRepository upstreamPoolRepository,
      UpstreamTargetRepository upstreamTargetRepository,
      ApiDefinitionService apiDefinitionService) {
    this.upstreamPoolRepository = upstreamPoolRepository;
    this.upstreamTargetRepository = upstreamTargetRepository;
    this.apiDefinitionService = apiDefinitionService;
  }

  public Mono<UpstreamPoolEntity> createPool(UUID apiId, String name, String loadBalancing) {
    if (!"ROUND_ROBIN".equals(loadBalancing)) {
      return Mono.error(
          ControlPlaneException.invalidRequest("Unsupported load balancing algorithm"));
    }
    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api -> {
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              UpstreamPoolEntity entity =
                  new UpstreamPoolEntity(
                      UUID.randomUUID(), api.id(), name, loadBalancing, now, now);
              return upstreamPoolRepository
                  .save(entity)
                  .onErrorMap(
                      DataIntegrityViolationException.class,
                      ex ->
                          ControlPlaneException.conflict(
                              "Upstream pool name already exists for API"));
            });
  }

  public Flux<UpstreamPoolEntity> listPools(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(upstreamPoolRepository.findByApiId(apiId));
  }

  public Mono<UpstreamPoolEntity> getPool(UUID poolId) {
    return upstreamPoolRepository
        .findById(poolId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Upstream pool was not found")));
  }

  public Mono<UpstreamTargetEntity> createTarget(
      UUID poolId, String url, boolean enabled, int weight) {
    if (weight <= 0) {
      return Mono.error(ControlPlaneException.invalidRequest("Target weight must be positive"));
    }
    try {
      UpstreamUriValidator.validate(URI.create(url), "target");
    } catch (RuntimeException ex) {
      return Mono.error(ControlPlaneException.invalidRequest(ex.getMessage()));
    }
    return getPool(poolId)
        .flatMap(
            pool -> {
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              UpstreamTargetEntity entity =
                  new UpstreamTargetEntity(
                      UUID.randomUUID(), pool.id(), url, enabled, weight, now, now);
              return upstreamTargetRepository
                  .save(entity)
                  .onErrorMap(
                      DataIntegrityViolationException.class,
                      ex ->
                          ControlPlaneException.conflict(
                              "Target URL already exists for upstream pool"));
            });
  }

  public Flux<UpstreamTargetEntity> listTargets(UUID poolId) {
    return getPool(poolId).thenMany(upstreamTargetRepository.findByUpstreamPoolId(poolId));
  }
}
