package com.autoapi.controlplane.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface GatewayRepository extends ReactiveCrudRepository<GatewayEntity, String> {

  Flux<GatewayEntity> findByGatewayGroup(String gatewayGroup);
}
