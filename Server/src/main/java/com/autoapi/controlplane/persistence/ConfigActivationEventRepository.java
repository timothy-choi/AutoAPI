package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConfigActivationEventRepository
    extends ReactiveCrudRepository<ConfigActivationEventEntity, UUID> {

  Mono<ConfigActivationEventEntity> findByGatewayIdAndReportId(String gatewayId, UUID reportId);

  Flux<ConfigActivationEventEntity> findByApiIdOrderByCreatedAtDesc(UUID apiId);
}
