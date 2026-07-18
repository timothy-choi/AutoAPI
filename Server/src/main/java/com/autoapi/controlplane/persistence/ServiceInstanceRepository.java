package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ServiceInstanceRepository
    extends ReactiveCrudRepository<ServiceInstanceEntity, UUID> {

  Flux<ServiceInstanceEntity> findByServiceId(UUID serviceId);

  Mono<ServiceInstanceEntity> findByServiceIdAndInstanceId(UUID serviceId, String instanceId);
}
