package com.autoapi.controlplane.traffic;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepositoryCustom;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.persistence.RouteRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TrafficSplitRouteBindingService {

  private final RouteRepository routeRepository;
  private final RouteRepositoryCustom routeRepositoryCustom;
  private final RoutePolicyBindingRepository bindingRepository;
  private final RoutePolicyBindingRepositoryCustom bindingRepositoryCustom;
  private final TrafficSplitPolicyService trafficSplitPolicyService;

  public TrafficSplitRouteBindingService(
      RouteRepository routeRepository,
      RouteRepositoryCustom routeRepositoryCustom,
      RoutePolicyBindingRepository bindingRepository,
      RoutePolicyBindingRepositoryCustom bindingRepositoryCustom,
      TrafficSplitPolicyService trafficSplitPolicyService) {
    this.routeRepository = routeRepository;
    this.routeRepositoryCustom = routeRepositoryCustom;
    this.bindingRepository = bindingRepository;
    this.bindingRepositoryCustom = bindingRepositoryCustom;
    this.trafficSplitPolicyService = trafficSplitPolicyService;
  }

  public Mono<RoutePolicyBindingEntity> bindTrafficSplitPolicy(
      UUID routeId, UUID trafficSplitPolicyId) {
    if (trafficSplitPolicyId == null) {
      return Mono.error(ControlPlaneException.invalidRequest("trafficSplitPolicyId is required"));
    }
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                validatePolicy(route, trafficSplitPolicyId)
                    .then(upsertBinding(route, trafficSplitPolicyId)));
  }

  public Mono<RoutePolicyBindingEntity> clearTrafficSplitPolicy(UUID routeId) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                bindingRepository
                    .findById(routeId)
                    .switchIfEmpty(
                        Mono.error(ControlPlaneException.notFound("Route has no policy binding")))
                    .flatMap(
                        existing -> {
                          if (existing.trafficSplitPolicyId() == null) {
                            return Mono.error(
                                ControlPlaneException.notFound(
                                    "Route has no traffic split policy binding"));
                          }
                          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                          RoutePolicyBindingEntity cleared =
                              new RoutePolicyBindingEntity(
                                  routeId,
                                  existing.authenticationRequired(),
                                  existing.rateLimitPolicyId(),
                                  existing.retryPolicyId(),
                                  null,
                                  existing.createdAt(),
                                  now);
                          if (!existing.authenticationRequired()
                              && existing.rateLimitPolicyId() == null
                              && existing.retryPolicyId() == null) {
                            return bindingRepository.deleteById(routeId).thenReturn(cleared);
                          }
                          return bindingRepositoryCustom.clearTrafficSplitPolicy(routeId, now);
                        }));
  }

  private Mono<Void> validatePolicy(RouteEntity route, UUID trafficSplitPolicyId) {
    return trafficSplitPolicyService
        .get(route.apiId(), trafficSplitPolicyId)
        .flatMap(
            policy -> {
              if (!policy.enabled()) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Disabled traffic split policy cannot be bound"));
              }
              return Mono.empty();
            });
  }

  private Mono<RoutePolicyBindingEntity> upsertBinding(
      RouteEntity route, UUID trafficSplitPolicyId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Mono<Void> clearDirectPool =
        route.upstreamPoolId() == null
            ? Mono.empty()
            : routeRepositoryCustom.clearUpstreamPool(route.id(), now).then();
    return clearDirectPool.then(
        bindingRepository
            .findById(route.id())
            .flatMap(
                existing ->
                    bindingRepositoryCustom.bindTrafficSplitPolicy(
                        route.id(), trafficSplitPolicyId, now))
            .switchIfEmpty(
                Mono.defer(
                    () ->
                        bindingRepository.save(
                            new RoutePolicyBindingEntity(
                                route.id(), false, null, null, trafficSplitPolicyId, now, now)))));
  }
}
