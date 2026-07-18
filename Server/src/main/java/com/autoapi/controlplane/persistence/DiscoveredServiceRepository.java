package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DiscoveredServiceRepository
    extends ReactiveCrudRepository<DiscoveredServiceEntity, UUID> {

  Flux<DiscoveredServiceEntity> findByProjectId(UUID projectId);

  Mono<DiscoveredServiceEntity> findByProjectIdAndName(UUID projectId, String name);

  Mono<DiscoveredServiceEntity> findByProjectIdAndId(UUID projectId, UUID id);
}
