package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiRepository extends ReactiveCrudRepository<ApiEntity, UUID> {
  Flux<ApiEntity> findByProjectId(UUID projectId);

  Mono<ApiEntity> findByProjectIdAndName(UUID projectId, String name);
}
