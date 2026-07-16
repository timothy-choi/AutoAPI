package com.autoapi.controlplane.routepolicy;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.RateLimitPolicyRepository;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.ratelimit.RateLimitPolicyService;
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
public class RoutePolicyBindingService {

  private final RouteRepository routeRepository;
  private final RoutePolicyBindingRepository bindingRepository;
  private final RateLimitPolicyRepository policyRepository;

  public RoutePolicyBindingService(
      RouteRepository routeRepository,
      RoutePolicyBindingRepository bindingRepository,
      RateLimitPolicyRepository policyRepository) {
    this.routeRepository = routeRepository;
    this.bindingRepository = bindingRepository;
    this.policyRepository = policyRepository;
  }

  public Mono<RoutePolicyBindingEntity> upsert(
      UUID routeId, boolean authenticationRequired, UUID rateLimitPolicyId) {
    if (rateLimitPolicyId != null && !authenticationRequired) {
      return Mono.error(
          ControlPlaneException.invalidRequest(
              "rateLimitPolicyId requires authenticationRequired=true"));
    }
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                validatePolicy(route, rateLimitPolicyId)
                    .then(saveBinding(routeId, authenticationRequired, rateLimitPolicyId)));
  }

  public Mono<RoutePolicyBindingEntity> get(UUID routeId) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .then(bindingRepository.findById(routeId))
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Route policy binding was not found")));
  }

  public Mono<Void> delete(UUID routeId) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .then(bindingRepository.deleteById(routeId))
        .then();
  }

  private Mono<Void> validatePolicy(RouteEntity route, UUID rateLimitPolicyId) {
    if (rateLimitPolicyId == null) {
      return Mono.empty();
    }
    return policyRepository
        .findById(rateLimitPolicyId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Rate limit policy was not found")))
        .flatMap(
            policy -> {
              if (!policy.apiId().equals(route.apiId())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Rate limit policy belongs to another API"));
              }
              if (!policy.enabled()) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Disabled rate limit policy cannot be bound"));
              }
              try {
                RateLimitPolicyService.validateFields(
                    policy.limitCount(),
                    policy.windowSeconds(),
                    policy.identitySource(),
                    policy.redisFailureMode());
              } catch (ControlPlaneException ex) {
                return Mono.error(ex);
              }
              return Mono.empty();
            });
  }

  private Mono<RoutePolicyBindingEntity> saveBinding(
      UUID routeId, boolean authenticationRequired, UUID rateLimitPolicyId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return bindingRepository
        .findById(routeId)
        .flatMap(
            existing ->
                bindingRepository.save(
                    new RoutePolicyBindingEntity(
                        routeId,
                        authenticationRequired,
                        rateLimitPolicyId,
                        existing.retryPolicyId(),
                        existing.trafficSplitPolicyId(),
                        existing.createdAt(),
                        now)))
        .switchIfEmpty(
            Mono.defer(
                () ->
                    bindingRepository.save(
                        new RoutePolicyBindingEntity(
                            routeId,
                            authenticationRequired,
                            rateLimitPolicyId,
                            null,
                            null,
                            now,
                            now))));
  }
}
