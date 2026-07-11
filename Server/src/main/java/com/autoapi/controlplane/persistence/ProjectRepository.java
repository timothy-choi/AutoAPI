package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ProjectRepository extends ReactiveCrudRepository<ProjectEntity, UUID> {
  Mono<ProjectEntity> findByName(String name);
}
