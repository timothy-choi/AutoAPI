package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TrafficSplitPolicyRepository
    extends ReactiveCrudRepository<TrafficSplitPolicyEntity, UUID> {

  Flux<TrafficSplitPolicyEntity> findByApiId(UUID apiId);
}
