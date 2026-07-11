package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface UpstreamTargetRepository
    extends ReactiveCrudRepository<UpstreamTargetEntity, UUID> {
  Flux<UpstreamTargetEntity> findByUpstreamPoolId(UUID upstreamPoolId);
}
