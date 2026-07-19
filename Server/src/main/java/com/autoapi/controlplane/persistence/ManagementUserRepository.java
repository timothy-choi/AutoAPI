package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ManagementUserRepository
    extends ReactiveCrudRepository<ManagementUserEntity, UUID> {
  Mono<ManagementUserEntity> findByOrganizationIdAndExternalSubject(
      UUID organizationId, String externalSubject);
}
