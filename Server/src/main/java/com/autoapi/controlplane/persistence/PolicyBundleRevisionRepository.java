package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PolicyBundleRevisionRepository
    extends ReactiveCrudRepository<PolicyBundleRevisionEntity, UUID> {
  Flux<PolicyBundleRevisionEntity> findByBundleIdOrderByRevisionNumberDesc(UUID bundleId);

  Mono<PolicyBundleRevisionEntity> findByBundleIdAndRevisionNumber(
      UUID bundleId, int revisionNumber);
}
