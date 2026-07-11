package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RouteRepository extends ReactiveCrudRepository<RouteEntity, UUID> {
  Flux<RouteEntity> findByApiId(UUID apiId);

  Mono<RouteEntity> findByApiIdAndName(UUID apiId, String name);
}
