package com.autoapi.controlplane.circuitbreaker;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyRepository;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepositoryCustom;
import com.autoapi.controlplane.persistence.RouteRepository;
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
public class CircuitBreakerRouteBindingService {

  private final RouteRepository routeRepository;
  private final RoutePolicyBindingRepository bindingRepository;
  private final RoutePolicyBindingRepositoryCustom bindingRepositoryCustom;
  private final CircuitBreakerPolicyRepository circuitBreakerPolicyRepository;

  public CircuitBreakerRouteBindingService(
      RouteRepository routeRepository,
      RoutePolicyBindingRepository bindingRepository,
      RoutePolicyBindingRepositoryCustom bindingRepositoryCustom,
      CircuitBreakerPolicyRepository circuitBreakerPolicyRepository) {
    this.routeRepository = routeRepository;
    this.bindingRepository = bindingRepository;
    this.bindingRepositoryCustom = bindingRepositoryCustom;
    this.circuitBreakerPolicyRepository = circuitBreakerPolicyRepository;
  }

  public Mono<RoutePolicyBindingEntity> bindCircuitBreakerPolicy(
      UUID routeId, UUID circuitBreakerPolicyId) {
    if (circuitBreakerPolicyId == null) {
      return Mono.error(ControlPlaneException.invalidRequest("circuitBreakerPolicyId is required"));
    }
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                validatePolicy(route, circuitBreakerPolicyId)
                    .then(upsertBinding(route, circuitBreakerPolicyId)));
  }

  public Mono<RoutePolicyBindingEntity> clearCircuitBreakerPolicy(UUID routeId) {
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
                          if (existing.circuitBreakerPolicyId() == null) {
                            return Mono.error(
                                ControlPlaneException.notFound(
                                    "Route has no circuit breaker policy binding"));
                          }
                          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                          RoutePolicyBindingEntity cleared =
                              new RoutePolicyBindingEntity(
                                  routeId,
                                  existing.authenticationRequired(),
                                  existing.rateLimitPolicyId(),
                                  existing.createdAt(),
                                  now,
                                  existing.retryPolicyId(),
                                  existing.trafficSplitPolicyId(),
                                  null);
                          if (!existing.authenticationRequired()
                              && existing.rateLimitPolicyId() == null
                              && existing.retryPolicyId() == null
                              && existing.trafficSplitPolicyId() == null) {
                            return bindingRepository.deleteById(routeId).thenReturn(cleared);
                          }
                          return bindingRepositoryCustom.clearCircuitBreakerPolicy(routeId, now);
                        }));
  }

  private Mono<Void> validatePolicy(RouteEntity route, UUID circuitBreakerPolicyId) {
    return circuitBreakerPolicyRepository
        .findById(circuitBreakerPolicyId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Circuit breaker policy was not found")))
        .flatMap(
            policy -> {
              if (!policy.apiId().equals(route.apiId())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Circuit breaker policy belongs to another API"));
              }
              if (!policy.enabled()) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Disabled circuit breaker policy cannot be bound"));
              }
              try {
                CircuitBreakerPolicyService.validateFields(
                    policy.failureThreshold(),
                    policy.rollingWindowSeconds(),
                    policy.openDurationSeconds(),
                    policy.halfOpenMaxRequests(),
                    policy.successThreshold(),
                    policy.predicateCountHttp5xx(),
                    policy.predicateCountConnectFailure(),
                    policy.predicateCountConnectTimeout(),
                    policy.predicateCountReadTimeout(),
                    policy.predicateCountTlsFailure(),
                    policy.predicateCountTransportException(),
                    policy.predicateCountHttp429());
              } catch (ControlPlaneException ex) {
                return Mono.error(ex);
              }
              return Mono.empty();
            });
  }

  private Mono<RoutePolicyBindingEntity> upsertBinding(
      RouteEntity route, UUID circuitBreakerPolicyId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return bindingRepository
        .findById(route.id())
        .flatMap(
            existing ->
                bindingRepositoryCustom.bindCircuitBreakerPolicy(
                    route.id(), circuitBreakerPolicyId, now))
        .switchIfEmpty(
            Mono.defer(
                () ->
                    bindingRepository.save(
                        new RoutePolicyBindingEntity(
                            route.id(),
                            false,
                            null,
                            now,
                            now,
                            null,
                            null,
                            circuitBreakerPolicyId))));
  }
}
