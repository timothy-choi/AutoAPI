package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ServiceAccountRepository
    extends ReactiveCrudRepository<ServiceAccountEntity, UUID> {
  Flux<ServiceAccountEntity> findByOrganizationId(UUID organizationId);

  Flux<ServiceAccountEntity> findByOrganizationIdAndProjectId(UUID organizationId, UUID projectId);

  Mono<ServiceAccountEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
