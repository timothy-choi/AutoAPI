package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RoutePolicyBindingRepository
    extends ReactiveCrudRepository<RoutePolicyBindingEntity, UUID> {
  Flux<RoutePolicyBindingEntity> findByRateLimitPolicyId(UUID rateLimitPolicyId);
}
