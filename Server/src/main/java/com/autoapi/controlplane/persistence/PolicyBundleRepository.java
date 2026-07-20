package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PolicyBundleRepository extends ReactiveCrudRepository<PolicyBundleEntity, UUID> {
  Flux<PolicyBundleEntity> findByOrganizationId(UUID organizationId);

  Mono<PolicyBundleEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

  Mono<PolicyBundleEntity> findByOrganizationIdAndName(UUID organizationId, String name);
}
