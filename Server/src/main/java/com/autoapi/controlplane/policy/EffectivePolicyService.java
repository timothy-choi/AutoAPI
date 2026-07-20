package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.persistence.RouteRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EffectivePolicyService {

  private final PolicyInheritanceResolver inheritanceResolver;
  private final PolicyMergeEngine mergeEngine;
  private final EffectivePolicyCache cache;
  private final RouteRepository routeRepository;
  private final PolicyEngineMetrics metrics;
  private final PolicyEngineTracer tracer;

  public EffectivePolicyService(
      PolicyInheritanceResolver inheritanceResolver,
      PolicyMergeEngine mergeEngine,
      EffectivePolicyCache cache,
      RouteRepository routeRepository,
      PolicyEngineMetrics metrics,
      PolicyEngineTracer tracer) {
    this.inheritanceResolver = inheritanceResolver;
    this.mergeEngine = mergeEngine;
    this.cache = cache;
    this.routeRepository = routeRepository;
    this.metrics = metrics;
    this.tracer = tracer;
  }

  public Mono<EffectivePolicyDocument> evaluateApi(UUID apiId, boolean explain) {
    return evaluateScope(cacheKeyApi(apiId), apiId, null, explain);
  }

  public Mono<EffectivePolicyDocument> evaluateRoute(UUID apiId, UUID routeId, boolean explain) {
    return evaluateScope(cacheKeyRoute(routeId), apiId, routeId, explain);
  }

  public Mono<Map<UUID, EffectivePolicyDocument>> evaluateAllRoutes(UUID apiId, boolean explain) {
    return routeRepository
        .findByApiId(apiId)
        .flatMap(
            route ->
                evaluateRoute(apiId, route.id(), explain)
                    .map(document -> Map.entry(route.id(), document)))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .map(map -> new LinkedHashMap<>(map));
  }

  private Mono<EffectivePolicyDocument> evaluateScope(
      String cacheKey, UUID apiId, UUID routeId, boolean explain) {
    metrics.resetEvaluationCounters();
    metrics.recordEvaluate();
    return cache.get(cacheKey).switchIfEmpty(computeAndCache(cacheKey, apiId, routeId, explain));
  }

  private Mono<EffectivePolicyDocument> computeAndCache(
      String cacheKey, UUID apiId, UUID routeId, boolean explain) {
    try (PolicyEngineTracer.PolicySpanScope resolveSpan = tracer.startPhase("resolve")) {
      return inheritanceResolver
          .resolve(apiId, routeId)
          .flatMap(
              contributions -> {
                try (PolicyEngineTracer.PolicySpanScope mergeSpan = tracer.startPhase("merge")) {
                  EffectivePolicyDocument document = mergeEngine.merge(contributions, explain);
                  return cache.put(cacheKey, document);
                }
              });
    }
  }

  public static String cacheKeyApi(UUID apiId) {
    return "api:" + apiId;
  }

  public static String cacheKeyRoute(UUID routeId) {
    return "route:" + routeId;
  }
}
