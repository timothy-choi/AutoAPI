package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ServiceRegistrationCredentialRepository
    extends ReactiveCrudRepository<ServiceRegistrationCredentialEntity, UUID> {

  Flux<ServiceRegistrationCredentialEntity> findByServiceId(UUID serviceId);

  Mono<ServiceRegistrationCredentialEntity> findByServiceIdAndCredentialId(
      UUID serviceId, String credentialId);
}
