package com.autoapi.controlplane;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.controlplane.persistence.ApiKeyRepository;
import com.autoapi.controlplane.persistence.ApiRepository;
import com.autoapi.controlplane.persistence.BackendHealthPolicyEntity;
import com.autoapi.controlplane.persistence.BackendHealthPolicyRepository;
import com.autoapi.controlplane.persistence.RateLimitPolicyEntity;
import com.autoapi.controlplane.persistence.RateLimitPolicyRepository;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RouteRepository;
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
      RoutePolicyBindingRepository routePolicyBindingRepository,
      ControlPlaneProperties properties) {
    this.apiRepository = apiRepository;
    this.routeRepository = routeRepository;
    this.upstreamPoolRepository = upstreamPoolRepository;
    this.upstreamTargetRepository = upstreamTargetRepository;
    this.apiKeyRepository = apiKeyRepository;
    this.rateLimitPolicyRepository = rateLimitPolicyRepository;
    this.backendHealthPolicyRepository = backendHealthPolicyRepository;
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
                        routePolicyBindingRepository.findAll().collectList())
                    .flatMap(
                        tuple -> {
                          List<UpstreamPoolEntity> pools = tuple.getT2();
                          List<RoutePolicyBindingEntity> allBindings = tuple.getT6();
                          List<RoutePolicyBindingEntity> apiBindings =
                              allBindings.stream()
                                  .filter(
                                      binding ->
                                          tuple.getT1().stream()
                                              .anyMatch(
                                                  route -> route.id().equals(binding.routeId())))
                                  .toList();
                          return reactor.core.publisher.Flux.fromIterable(pools)
                              .flatMap(
                                  pool -> upstreamTargetRepository.findByUpstreamPoolId(pool.id()))
                              .collectList()
                              .map(
                                  targets ->
                                      new DraftGraph(
                                          api,
                                          tuple.getT1(),
                                          pools,
                                          targets,
                                          tuple.getT3(),
                                          tuple.getT4(),
                                          tuple.getT5(),
                                          apiBindings,
                                          gatewayDefaults()));
                        }));
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
      List<RoutePolicyBindingEntity> routePolicyBindings,
      CompiledGatewaySection gatewayDefaults) {}
}
