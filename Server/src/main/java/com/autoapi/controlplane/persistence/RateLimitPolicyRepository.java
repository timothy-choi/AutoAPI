package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RateLimitPolicyRepository
    extends ReactiveCrudRepository<RateLimitPolicyEntity, UUID> {
  Flux<RateLimitPolicyEntity> findByApiId(UUID apiId);

  Mono<RateLimitPolicyEntity> findByApiIdAndName(UUID apiId, String name);
}
