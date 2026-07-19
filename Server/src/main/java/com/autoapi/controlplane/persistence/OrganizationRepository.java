package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OrganizationRepository extends ReactiveCrudRepository<OrganizationEntity, UUID> {
  Mono<OrganizationEntity> findBySlug(String slug);
}
