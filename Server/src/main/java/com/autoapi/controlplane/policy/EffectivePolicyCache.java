package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.persistence.GlobalPolicyCacheGenerationRepositoryCustom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** In-memory effective policy cache with global generation invalidation. */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EffectivePolicyCache {

  private final GlobalPolicyCacheGenerationRepositoryCustom generationRepository;
  private final PolicyEngineMetrics metrics;
  private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private volatile long loadedGeneration = -1L;

  public EffectivePolicyCache(
      GlobalPolicyCacheGenerationRepositoryCustom generationRepository,
      PolicyEngineMetrics metrics) {
    this.generationRepository = generationRepository;
    this.metrics = metrics;
  }

  public Mono<EffectivePolicyDocument> get(String scopeKey) {
    return ensureGeneration()
        .flatMap(
            generation -> {
              CacheEntry entry = cache.get(scopeKey);
              if (entry != null && entry.generation() == generation) {
                metrics.recordCacheHit();
                return Mono.just(entry.document());
              }
              metrics.recordCacheMiss();
              return Mono.empty();
            });
  }

  public Mono<EffectivePolicyDocument> put(String scopeKey, EffectivePolicyDocument document) {
    return ensureGeneration()
        .doOnNext(generation -> cache.put(scopeKey, new CacheEntry(generation, document)))
        .thenReturn(document);
  }

  public void invalidateAll() {
    cache.clear();
    loadedGeneration = -1L;
  }

  public Mono<Long> currentGeneration() {
    return ensureGeneration();
  }

  private Mono<Long> ensureGeneration() {
    return generationRepository
        .getGeneration()
        .doOnNext(
            generation -> {
              if (loadedGeneration >= 0 && generation != loadedGeneration) {
                cache.clear();
              }
              loadedGeneration = generation;
            });
  }

  public Map<String, CacheEntry> snapshot() {
    return Map.copyOf(cache);
  }

  public record CacheEntry(long generation, EffectivePolicyDocument document) {}
}
