package com.autoapi.controlplane.retry;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.RetryPolicyRepository;
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
public class RetryRouteBindingService {

  private final RouteRepository routeRepository;
  private final RoutePolicyBindingRepository bindingRepository;
  private final RoutePolicyBindingRepositoryCustom bindingRepositoryCustom;
  private final RetryPolicyRepository retryPolicyRepository;

  public RetryRouteBindingService(
      RouteRepository routeRepository,
      RoutePolicyBindingRepository bindingRepository,
      RoutePolicyBindingRepositoryCustom bindingRepositoryCustom,
      RetryPolicyRepository retryPolicyRepository) {
    this.routeRepository = routeRepository;
    this.bindingRepository = bindingRepository;
    this.bindingRepositoryCustom = bindingRepositoryCustom;
    this.retryPolicyRepository = retryPolicyRepository;
  }

  public Mono<RoutePolicyBindingEntity> bindRetryPolicy(UUID routeId, UUID retryPolicyId) {
    if (retryPolicyId == null) {
      return Mono.error(ControlPlaneException.invalidRequest("retryPolicyId is required"));
    }
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                validatePolicy(route, retryPolicyId).then(upsertBinding(route, retryPolicyId)));
  }

  public Mono<RoutePolicyBindingEntity> clearRetryPolicy(UUID routeId) {
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
                          if (existing.retryPolicyId() == null) {
                            return Mono.error(
                                ControlPlaneException.notFound(
                                    "Route has no retry policy binding"));
                          }
                          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                          RoutePolicyBindingEntity cleared =
                              new RoutePolicyBindingEntity(
                                  routeId,
                                  existing.authenticationRequired(),
                                  existing.rateLimitPolicyId(),
                                  null,
                                  existing.createdAt(),
                                  now);
                          if (!existing.authenticationRequired()
                              && existing.rateLimitPolicyId() == null) {
                            return bindingRepository.deleteById(routeId).thenReturn(cleared);
                          }
                          return bindingRepositoryCustom.clearRetryPolicy(routeId, now);
                        }));
  }

  private Mono<Void> validatePolicy(RouteEntity route, UUID retryPolicyId) {
    return retryPolicyRepository
        .findById(retryPolicyId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Retry policy was not found")))
        .flatMap(
            policy -> {
              if (!policy.apiId().equals(route.apiId())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest("Retry policy belongs to another API"));
              }
              if (!policy.enabled()) {
                return Mono.error(
                    ControlPlaneException.invalidRequest("Disabled retry policy cannot be bound"));
              }
              try {
                RetryPolicyService.validateFields(
                    policy.maxAttempts(),
                    policy.perAttemptTimeoutMs(),
                    policy.retryOnConnectFailure(),
                    policy.retryOnConnectionReset(),
                    policy.retryOnDnsFailure(),
                    policy.retryOnResponseTimeout(),
                    policy.retryableMethods(),
                    policy.requireIdempotencyKeyForUnsafeMethods(),
                    policy.budgetPercent(),
                    policy.budgetMinRetriesPerSecond(),
                    policy.budgetWindowSeconds());
              } catch (ControlPlaneException ex) {
                return Mono.error(ex);
              }
              return Mono.empty();
            });
  }

  private Mono<RoutePolicyBindingEntity> upsertBinding(RouteEntity route, UUID retryPolicyId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return bindingRepository
        .findById(route.id())
        .flatMap(
            existing -> bindingRepositoryCustom.bindRetryPolicy(route.id(), retryPolicyId, now))
        .switchIfEmpty(
            Mono.defer(
                () ->
                    bindingRepository.save(
                        new RoutePolicyBindingEntity(
                            route.id(), false, null, retryPolicyId, now, now))));
  }
}
