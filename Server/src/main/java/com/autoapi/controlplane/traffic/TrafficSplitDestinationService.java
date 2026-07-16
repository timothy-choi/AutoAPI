package com.autoapi.controlplane.traffic;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationRepository;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationRepositoryCustom;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TrafficSplitDestinationService {

  private final TrafficSplitPolicyService policyService;
  private final TrafficSplitDestinationRepository destinationRepository;
  private final TrafficSplitDestinationRepositoryCustom destinationRepositoryCustom;
  private final UpstreamPoolRepository upstreamPoolRepository;

  public TrafficSplitDestinationService(
      TrafficSplitPolicyService policyService,
      TrafficSplitDestinationRepository destinationRepository,
      TrafficSplitDestinationRepositoryCustom destinationRepositoryCustom,
      UpstreamPoolRepository upstreamPoolRepository) {
    this.policyService = policyService;
    this.destinationRepository = destinationRepository;
    this.destinationRepositoryCustom = destinationRepositoryCustom;
    this.upstreamPoolRepository = upstreamPoolRepository;
  }

  public Mono<TrafficSplitDestinationEntity> addDestination(
      UUID policyId, String name, UUID upstreamPoolId, int weight, int priority, boolean primary) {
    if (name == null || name.isBlank()) {
      return Mono.error(ControlPlaneException.invalidRequest("name is required"));
    }
    TrafficSplitPolicyService.validateDestinationWeight(weight);
    TrafficSplitPolicyService.validateDestinationPriority(priority);
    return policyService
        .getByIdOnly(policyId)
        .flatMap(
            policy ->
                validatePoolOwnership(policy, upstreamPoolId)
                    .then(
                        Mono.defer(
                            () -> {
                              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                              TrafficSplitDestinationEntity entity =
                                  new TrafficSplitDestinationEntity(
                                      UUID.randomUUID(),
                                      policyId,
                                      upstreamPoolId,
                                      name.trim(),
                                      weight,
                                      priority,
                                      primary,
                                      now,
                                      now);
                              return destinationRepository
                                  .save(entity)
                                  .onErrorResume(
                                      DataIntegrityViolationException.class,
                                      ex ->
                                          Mono.error(
                                              ControlPlaneException.conflict(
                                                  "Destination name or pool binding already exists")));
                            })));
  }

  public Mono<TrafficSplitDestinationEntity> patchDestination(
      UUID policyId,
      UUID destinationId,
      String name,
      Integer weight,
      Integer priority,
      Boolean primary) {
    return destinationRepository
        .findByTrafficSplitPolicyIdAndId(policyId, destinationId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Traffic split destination was not found")))
        .flatMap(
            existing -> {
              String mergedName = name == null ? existing.name() : name.trim();
              if (mergedName.isBlank()) {
                return Mono.error(ControlPlaneException.invalidRequest("name must not be blank"));
              }
              int mergedWeight = weight == null ? existing.weight() : weight;
              int mergedPriority = priority == null ? existing.priority() : priority;
              boolean mergedPrimary = primary == null ? existing.primary() : primary;
              TrafficSplitPolicyService.validateDestinationWeight(mergedWeight);
              TrafficSplitPolicyService.validateDestinationPriority(mergedPriority);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return destinationRepositoryCustom
                  .patchDestination(
                      destinationId, mergedName, mergedWeight, mergedPriority, mergedPrimary, now)
                  .onErrorResume(
                      DataIntegrityViolationException.class,
                      ex ->
                          Mono.error(
                              ControlPlaneException.conflict(
                                  "Destination name already exists for this policy")));
            });
  }

  public Mono<Void> deleteDestination(UUID policyId, UUID destinationId) {
    return destinationRepository
        .findByTrafficSplitPolicyIdAndId(policyId, destinationId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Traffic split destination was not found")))
        .flatMap(entity -> destinationRepository.deleteById(entity.id()));
  }

  private Mono<UpstreamPoolEntity> validatePoolOwnership(
      TrafficSplitPolicyEntity policy, UUID upstreamPoolId) {
    return upstreamPoolRepository
        .findById(upstreamPoolId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Upstream pool was not found")))
        .flatMap(
            pool -> {
              if (!pool.apiId().equals(policy.apiId())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest("Upstream pool belongs to another API"));
              }
              return Mono.just(pool);
            });
  }
}
