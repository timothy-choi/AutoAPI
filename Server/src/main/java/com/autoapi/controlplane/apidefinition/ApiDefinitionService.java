package com.autoapi.controlplane.apidefinition;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.ApiRepository;
import com.autoapi.controlplane.project.ProjectService;
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
public class ApiDefinitionService {

  private final ApiRepository apiRepository;
  private final ProjectService projectService;

  public ApiDefinitionService(ApiRepository apiRepository, ProjectService projectService) {
    this.apiRepository = apiRepository;
    this.projectService = projectService;
  }

  public Mono<ApiEntity> create(UUID projectId, String name, String host, String basePath) {
    return projectService.get(projectId).then(createEntity(projectId, name, host, basePath));
  }

  private Mono<ApiEntity> createEntity(UUID projectId, String name, String host, String basePath) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ApiEntity entity =
        new ApiEntity(
            UUID.randomUUID(),
            projectId,
            name,
            host,
            basePath == null || basePath.isBlank() ? "/" : basePath,
            true,
            null,
            now,
            now);
    return apiRepository
        .save(entity)
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("API name already exists for project"));
  }

  public Flux<ApiEntity> listByProject(UUID projectId) {
    return projectService.get(projectId).thenMany(apiRepository.findByProjectId(projectId));
  }

  public Mono<ApiEntity> get(UUID apiId) {
    return apiRepository
        .findById(apiId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("API was not found")));
  }
}
