package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConfigVersionRepository extends ReactiveCrudRepository<ConfigVersionEntity, UUID> {
  Flux<ConfigVersionEntity> findByApiIdOrderByVersionDesc(UUID apiId);

  Mono<ConfigVersionEntity> findByApiIdAndVersion(UUID apiId, long version);

  Mono<ConfigVersionEntity> findByApiIdAndContentHash(UUID apiId, String contentHash);
}
