package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ManagementAccessCredentialRepository
    extends ReactiveCrudRepository<ManagementAccessCredentialEntity, UUID> {
  Mono<ManagementAccessCredentialEntity> findByPublicTokenId(String publicTokenId);

  Flux<ManagementAccessCredentialEntity> findByPrincipalTypeAndPrincipalId(
      String principalType, UUID principalId);
}
