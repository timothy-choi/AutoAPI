package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RoleBindingRepository extends ReactiveCrudRepository<RoleBindingEntity, UUID> {
  Flux<RoleBindingEntity> findByOrganizationIdAndRevokedAtIsNull(UUID organizationId);

  Flux<RoleBindingEntity> findByOrganizationIdAndProjectIdAndRevokedAtIsNull(
      UUID organizationId, UUID projectId);

  Flux<RoleBindingEntity> findByPrincipalTypeAndPrincipalIdAndRevokedAtIsNull(
      String principalType, UUID principalId);
}
