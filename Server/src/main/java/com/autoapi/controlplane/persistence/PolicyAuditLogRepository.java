package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PolicyAuditLogRepository
    extends ReactiveCrudRepository<PolicyAuditLogEntity, UUID> {
  Flux<PolicyAuditLogEntity> findTop50ByOrderByCreatedAtDesc();
}
