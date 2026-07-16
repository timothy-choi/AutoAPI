package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrafficSplitDestinationRepository
    extends ReactiveCrudRepository<TrafficSplitDestinationEntity, UUID> {

  Flux<TrafficSplitDestinationEntity> findByTrafficSplitPolicyId(UUID trafficSplitPolicyId);

  Mono<TrafficSplitDestinationEntity> findByTrafficSplitPolicyIdAndId(
      UUID trafficSplitPolicyId, UUID destinationId);
}
