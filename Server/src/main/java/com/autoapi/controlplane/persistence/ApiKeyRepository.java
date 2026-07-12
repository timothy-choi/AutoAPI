package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiKeyRepository extends ReactiveCrudRepository<ApiKeyEntity, UUID> {
  Flux<ApiKeyEntity> findByApiId(UUID apiId);

  Mono<ApiKeyEntity> findByApiIdAndKeyId(UUID apiId, String keyId);

  Mono<ApiKeyEntity> findByApiIdAndName(UUID apiId, String name);
}
