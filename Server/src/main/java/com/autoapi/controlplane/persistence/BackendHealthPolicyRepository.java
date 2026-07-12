package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BackendHealthPolicyRepository
    extends ReactiveCrudRepository<BackendHealthPolicyEntity, UUID> {

  Flux<BackendHealthPolicyEntity> findByApiId(UUID apiId);

  Mono<BackendHealthPolicyEntity> findByApiIdAndName(UUID apiId, String name);
}
