package com.autoapi.controlplane.policy.bundle;

import com.autoapi.controlplane.persistence.GlobalPolicyCacheGenerationRepositoryCustom;
import com.autoapi.controlplane.policy.EffectivePolicyCache;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyCacheInvalidator {

  private final GlobalPolicyCacheGenerationRepositoryCustom generationRepository;
  private final EffectivePolicyCache effectivePolicyCache;

  public PolicyCacheInvalidator(
      GlobalPolicyCacheGenerationRepositoryCustom generationRepository,
      EffectivePolicyCache effectivePolicyCache) {
    this.generationRepository = generationRepository;
    this.effectivePolicyCache = effectivePolicyCache;
  }

  public Mono<Long> invalidate() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    effectivePolicyCache.invalidateAll();
    return generationRepository.bumpGeneration(now);
  }
}
