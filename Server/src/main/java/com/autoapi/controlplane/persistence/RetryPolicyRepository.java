package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RetryPolicyRepository extends ReactiveCrudRepository<RetryPolicyEntity, UUID> {

  Flux<RetryPolicyEntity> findByApiId(UUID apiId);
}
