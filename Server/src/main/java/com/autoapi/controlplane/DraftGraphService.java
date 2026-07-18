package com.autoapi.controlplane;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.ApiKeyRepository;
import com.autoapi.controlplane.persistence.ApiRepository;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.BackendHealthPolicyRepository;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyEntity;
import com.autoapi.controlplane.persistence.CircuitBreakerPolicyRepository;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyRepository;
import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RetryPolicyRepository;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationEntity;
import com.autoapi.controlplane.persistence.TrafficSplitDestinationRepository;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyEntity;
import com.autoapi.controlplane.persistence.TrafficSplitPolicyRepository;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolRepository;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DraftGraphService {

  private final ApiRepository apiRepository;
  private final RouteRepository routeRepository;
  private final UpstreamPoolRepository upstreamPoolRepository;
  private final UpstreamTargetRepository upstreamTargetRepository;
  private final ApiKeyRepository apiKeyRepository;
  private final RateLimitPolicyRepository rateLimitPolicyRepository;
  private final BackendHealthPolicyRepository backendHealthPolicyRepository;
  private final RetryPolicyRepository retryPolicyRepository;
  private final CircuitBreakerPolicyRepository circuitBreakerPolicyRepository;
  private final TrafficSplitPolicyRepository trafficSplitPolicyRepository;
  private final TrafficSplitDestinationRepository trafficSplitDestinationRepository;
  private final RoutePolicyBindingRepository routePolicyBindingRepository;
  private final ControlPlaneProperties properties;

  public DraftGraphService(
      ApiRepository apiRepository,
      RouteRepository routeRepository,
      UpstreamPoolRepository upstreamPoolRepository,
      UpstreamTargetRepository upstreamTargetRepository,
      ApiKeyRepository apiKeyRepository,
      RateLimitPolicyRepository rateLimitPolicyRepository,
      BackendHealthPolicyRepository backendHealthPolicyRepository,
      RetryPolicyRepository retryPolicyRepository,
      CircuitBreakerPolicyRepository circuitBreakerPolicyRepository,
      TrafficSplitPolicyRepository trafficSplitPolicyRepository,
      TrafficSplitDestinationRepository trafficSplitDestinationRepository,
      RoutePolicyBindingRepository routePolicyBindingRepository,
      ControlPlaneProperties properties) {
    this.apiRepository = apiRepository;
    this.routeRepository = routeRepository;
    this.upstreamPoolRepository = upstreamPoolRepository;
    this.upstreamTargetRepository = upstreamTargetRepository;
    this.apiKeyRepository = apiKeyRepository;
    this.rateLimitPolicyRepository = rateLimitPolicyRepository;
    this.backendHealthPolicyRepository = backendHealthPolicyRepository;
    this.retryPolicyRepository = retryPolicyRepository;
    this.circuitBreakerPolicyRepository = circuitBreakerPolicyRepository;
    this.trafficSplitPolicyRepository = trafficSplitPolicyRepository;
    this.trafficSplitDestinationRepository = trafficSplitDestinationRepository;
    this.routePolicyBindingRepository = routePolicyBindingRepository;
    this.properties = properties;
  }

  public Mono<DraftGraph> loadByApiId(UUID apiId) {
    return apiRepository
        .findById(apiId)
        .flatMap(
            api ->
                Mono.zip(
                        routeRepository.findByApiId(apiId).collectList(),
                        upstreamPoolRepository.findByApiId(apiId).collectList(),
                        apiKeyRepository.findByApiId(apiId).collectList(),
                        rateLimitPolicyRepository.findByApiId(apiId).collectList(),
                        backendHealthPolicyRepository.findByApiId(apiId).collectList(),
                        retryPolicyRepository.findByApiId(apiId).collectList(),
                        trafficSplitPolicyRepository.findByApiId(apiId).collectList(),
                        routePolicyBindingRepository.findAll().collectList())
                    .flatMap(
                        tuple ->
                            circuitBreakerPolicyRepository
                                .findByApiId(apiId)
                                .collectList()
                                .flatMap(
                                    circuitBreakerPolicies -> {
                                      List<UpstreamPoolEntity> pools = tuple.getT2();
                                      List<RoutePolicyBindingEntity> allBindings = tuple.getT8();
                                      List<RoutePolicyBindingEntity> apiBindings =
                                          allBindings.stream()
                                              .filter(
                                                  binding ->
                                                      tuple.getT1().stream()
                                                          .anyMatch(
                                                              route ->
                                                                  route
                                                                      .id()
                                                                      .equals(binding.routeId())))
                                              .toList();
                                      return reactor.core.publisher.Flux.fromIterable(pools)
                                          .flatMap(
                                              pool ->
                                                  upstreamTargetRepository.findByUpstreamPoolId(
                                                      pool.id()))
                                          .collectList()
                                          .flatMap(
                                              targets ->
                                                  reactor.core.publisher.Flux.fromIterable(
                                                          tuple.getT7())
                                                      .flatMap(
                                                          policy ->
                                                              trafficSplitDestinationRepository
                                                                  .findByTrafficSplitPolicyId(
                                                                      policy.id()))
                                                      .collectList()
                                                      .map(
                                                          splitDestinations ->
                                                              new DraftGraph(
                                                                  api,
                                                                  tuple.getT1(),
                                                                  pools,
                                                                  targets,
                                                                  tuple.getT3(),
                                                                  tuple.getT4(),
                                                                  tuple.getT5(),
                                                                  tuple.getT6(),
                                                                  circuitBreakerPolicies,
                                                                  tuple.getT7(),
                                                                  splitDestinations,
                                                                  apiBindings,
                                                                  gatewayDefaults())));
                                    })));
  }

  public CompiledGatewaySection gatewayDefaults() {
    return new CompiledGatewaySection(
        properties.compiledGateway().listenAddress(), properties.compiledGateway().port());
  }

  public record DraftGraph(
      ApiEntity api,
      List<RouteEntity> routes,
      List<UpstreamPoolEntity> pools,
      List<UpstreamTargetEntity> targets,
      List<ApiKeyEntity> apiKeys,
      List<RateLimitPolicyEntity> rateLimitPolicies,
      List<BackendHealthPolicyEntity> backendHealthPolicies,
      List<RetryPolicyEntity> retryPolicies,
      List<CircuitBreakerPolicyEntity> circuitBreakerPolicies,
      List<TrafficSplitPolicyEntity> trafficSplitPolicies,
      List<TrafficSplitDestinationEntity> trafficSplitDestinations,
      List<RoutePolicyBindingEntity> routePolicyBindings,
      CompiledGatewaySection gatewayDefaults) {}
}
