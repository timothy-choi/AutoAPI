package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PolicyBundleAssignmentRepository
    extends ReactiveCrudRepository<PolicyBundleAssignmentEntity, UUID> {
  Flux<PolicyBundleAssignmentEntity> findByBundleId(UUID bundleId);
}
