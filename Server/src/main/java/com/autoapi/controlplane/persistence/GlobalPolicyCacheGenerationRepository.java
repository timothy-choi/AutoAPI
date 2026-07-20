package com.autoapi.controlplane.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface GlobalPolicyCacheGenerationRepository
    extends ReactiveCrudRepository<GlobalPolicyCacheGenerationEntity, Integer> {
  Mono<GlobalPolicyCacheGenerationEntity> findById(int id);
}
