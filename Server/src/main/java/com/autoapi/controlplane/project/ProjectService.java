package com.autoapi.controlplane.project;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.ProjectEntity;
import com.autoapi.controlplane.persistence.ProjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProjectService {

  private final ProjectRepository projectRepository;

  public ProjectService(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  public Mono<ProjectEntity> create(String name, String description) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ProjectEntity entity = new ProjectEntity(UUID.randomUUID(), name, description, now, now);
    return projectRepository
        .save(entity)
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Project name already exists"))
        .onErrorMap(
            DataAccessException.class,
            ex -> ControlPlaneException.internal("Failed to create project"));
  }

  public Flux<ProjectEntity> list() {
    return projectRepository.findAll();
  }

  public Mono<ProjectEntity> get(UUID projectId) {
    return projectRepository
        .findById(projectId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Project was not found")));
  }
}
