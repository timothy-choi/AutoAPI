package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UpstreamPoolRepository extends ReactiveCrudRepository<UpstreamPoolEntity, UUID> {
  Flux<UpstreamPoolEntity> findByApiId(UUID apiId);

  Mono<UpstreamPoolEntity> findByApiIdAndName(UUID apiId, String name);
}
