package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CircuitBreakerPolicyRepository
    extends ReactiveCrudRepository<CircuitBreakerPolicyEntity, UUID> {

  Flux<CircuitBreakerPolicyEntity> findByApiId(UUID apiId);
}
