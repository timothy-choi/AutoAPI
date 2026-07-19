package com.autoapi.controlplane.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ManagementAuthStateRepository
    extends ReactiveCrudRepository<ManagementAuthStateEntity, Integer> {
  Mono<ManagementAuthStateEntity> findById(Integer id);
}
