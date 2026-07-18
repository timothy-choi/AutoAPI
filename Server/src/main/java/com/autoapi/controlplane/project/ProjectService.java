package com.autoapi.controlplane.project;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.ProjectEntity;
import com.autoapi.controlplane.persistence.ProjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final PlatformEventRecorder eventRecorder;

  public ProjectService(ProjectRepository projectRepository, PlatformEventRecorder eventRecorder) {
    this.projectRepository = projectRepository;
    this.eventRecorder = eventRecorder;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<ProjectEntity> create(String name, String description, EventContext context) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ProjectEntity entity = new ProjectEntity(UUID.randomUUID(), name, description, now, now);
    EventContext eventContext = context != null ? context : EventContext.managementApi(null);
    return projectRepository
        .save(entity)
        .flatMap(
            saved ->
                eventRecorder
                    .record(
                        RecordPlatformEventRequest.of(
                            PlatformEventTypes.PROJECT_CREATED,
                            saved.id(),
                            null,
                            "PROJECT",
                            saved.id().toString(),
                            eventContext,
                            Map.of("name", saved.name())))
                    .thenReturn(saved))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Project name already exists"))
        .onErrorMap(
            DataAccessException.class,
            ex -> ControlPlaneException.internal("Failed to create project"));
  }

  public Mono<ProjectEntity> create(String name, String description) {
    return create(name, description, EventContext.managementApi(null));
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
