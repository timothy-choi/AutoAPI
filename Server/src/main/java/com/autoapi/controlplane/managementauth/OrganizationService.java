package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.OrganizationEntity;
import com.autoapi.controlplane.persistence.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  public OrganizationService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  public Mono<OrganizationEntity> create(
      ManagementPrincipal caller, String name, String slug, String description) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OrganizationEntity entity =
        new OrganizationEntity(
            UUID.randomUUID(), name.trim(), slug.trim(), "ACTIVE", now, now, null);
    return organizationRepository
        .save(entity)
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Organization slug already exists"));
  }

  public Flux<OrganizationEntity> list() {
    return organizationRepository.findAll();
  }

  public Mono<OrganizationEntity> get(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Organization was not found")));
  }
}
