package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import com.autoapi.controlplane.persistence.GatewayGroupEntity;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom.GatewayGroupMembershipRow;
import com.autoapi.controlplane.persistence.RuntimeRolloutEntity;
import com.autoapi.controlplane.persistence.RuntimeRolloutGatewayEntity;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom.GatewayPlacementRow;
import com.autoapi.controlplane.persistence.RuntimeRolloutStageEntity;
import com.autoapi.controlplane.project.ProjectService;
import com.autoapi.controlplane.rollout.RolloutStageCalculator.StageDefinitionInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class RuntimeRolloutService {

  private static final Logger log = LoggerFactory.getLogger(RuntimeRolloutService.class);
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PREVIEW_SAMPLE = 20;

  private final RuntimeRolloutRepositoryCustom rolloutRepository;
  private final GatewayGroupRepositoryCustom gatewayGroupRepository;
  private final ConfigVersionRepository configVersionRepository;
  private final ProjectService projectService;
  private final ApiDefinitionService apiDefinitionService;
  private final PlatformEventRecorder eventRecorder;
  private final RolloutsProperties properties;
  private final ControlPlaneProperties controlPlaneProperties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public RuntimeRolloutService(
      RuntimeRolloutRepositoryCustom rolloutRepository,
      GatewayGroupRepositoryCustom gatewayGroupRepository,
      ConfigVersionRepository configVersionRepository,
      ProjectService projectService,
      ApiDefinitionService apiDefinitionService,
      PlatformEventRecorder eventRecorder,
      RolloutsProperties properties,
      ControlPlaneProperties controlPlaneProperties,
      ObjectMapper objectMapper,
      Clock eventsClock) {
    this.rolloutRepository = rolloutRepository;
    this.gatewayGroupRepository = gatewayGroupRepository;
    this.configVersionRepository = configVersionRepository;
    this.projectService = projectService;
    this.apiDefinitionService = apiDefinitionService;
    this.eventRecorder = eventRecorder;
    this.properties = properties;
    this.controlPlaneProperties = controlPlaneProperties;
    this.objectMapper = objectMapper;
    this.clock = eventsClock;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> createDraft(
      UUID projectId,
      UUID gatewayGroupId,
      long targetVersion,
      String strategy,
      String progressionMode,
      Boolean autoRollbackOnFailure,
      String cancelBehavior,
      List<StageDefinitionInput> stages,
      EventContext context) {
    UUID rolloutId = UUID.randomUUID();
    OffsetDateTime now = now();
    return projectService
        .get(projectId)
        .then(requireGroup(projectId, gatewayGroupId))
        .flatMap(
            group ->
                validateVersions(group.apiId(), targetVersion)
                    .then(resolveSourceVersion(group))
                    .flatMap(
                        sourceVersion -> {
                          List<StageDefinitionInput> effectiveStages =
                              effectiveStages(strategy, stages);
                          RolloutStageCalculator.validateStageDefinitions(
                              effectiveStages, properties.maxStages());
                          String effectiveProgression =
                              progressionMode == null
                                  ? properties.defaultProgressionMode().name()
                                  : progressionMode;
                          return rolloutRepository
                              .countActiveRolloutsByProject(projectId)
                              .flatMap(
                                  activeCount -> {
                                    if (activeCount >= properties.maxActiveRolloutsPerProject()) {
                                      return Mono.error(
                                          ControlPlaneException.invalidRequest(
                                              "Maximum active rollouts per project exceeded"));
                                    }
                                    return rolloutRepository
                                        .insertRollout(
                                            rolloutId,
                                            projectId,
                                            gatewayGroupId,
                                            group.apiId(),
                                            sourceVersion,
                                            targetVersion,
                                            strategy,
                                            effectiveProgression,
                                            "DRAFT",
                                            properties.gatewayMembership().mode().name(),
                                            autoRollbackOnFailure != null && autoRollbackOnFailure,
                                            cancelBehavior == null
                                                ? "KEEP_CURRENT_ASSIGNMENTS"
                                                : cancelBehavior,
                                            UUID.randomUUID(),
                                            context.actorType(),
                                            context.actorId(),
                                            now)
                                        .flatMap(
                                            rollout ->
                                                insertStageDefinitions(
                                                        rollout.id(), effectiveStages, now)
                                                    .then(
                                                        recordRolloutEvent(
                                                            rollout,
                                                            PlatformEventTypes
                                                                .RUNTIME_ROLLOUT_CREATED,
                                                            context))
                                                    .thenReturn(RuntimeRolloutView.from(rollout)));
                                  });
                        }))
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex ->
                ControlPlaneException.conflict(
                    "Conflicting rollout or active rollout already exists for group"));
  }

  public Mono<RolloutPreview> preview(
      UUID projectId,
      UUID gatewayGroupId,
      long targetVersion,
      String strategy,
      List<StageDefinitionInput> stages) {
    return requireGroup(projectId, gatewayGroupId)
        .flatMap(
            group ->
                validateVersions(group.apiId(), targetVersion)
                    .then(resolveSourceVersion(group))
                    .flatMap(
                        sourceVersion ->
                            buildPlan(
                                    group,
                                    sourceVersion,
                                    targetVersion,
                                    strategy,
                                    effectiveStages(strategy, stages),
                                    UUID.randomUUID())
                                .map(
                                    plan ->
                                        new RolloutPreview(
                                            gatewayGroupId,
                                            sourceVersion,
                                            targetVersion,
                                            plan.eligibleCount(),
                                            plan.excludedCount(),
                                            plan.offlineCount(),
                                            plan.incompatibleCount(),
                                            plan.stageCounts(),
                                            plan.sampleGatewayIds(),
                                            plan.warnings()))));
  }

  public Mono<RolloutPreview> previewExisting(UUID projectId, UUID rolloutId) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout ->
                gatewayGroupRepository
                    .findById(projectId, rollout.gatewayGroupId())
                    .flatMap(
                        group ->
                            rolloutRepository
                                .listStagesByRollout(rolloutId)
                                .map(this::toStageInput)
                                .collectList()
                                .flatMap(
                                    stages ->
                                        buildPlan(
                                                group,
                                                rollout.sourceVersion(),
                                                rollout.targetVersion(),
                                                rollout.strategy(),
                                                stages,
                                                rollout.id())
                                            .map(
                                                plan ->
                                                    new RolloutPreview(
                                                        rollout.gatewayGroupId(),
                                                        rollout.sourceVersion(),
                                                        rollout.targetVersion(),
                                                        plan.eligibleCount(),
                                                        plan.excludedCount(),
                                                        plan.offlineCount(),
                                                        plan.incompatibleCount(),
                                                        plan.stageCounts(),
                                                        plan.sampleGatewayIds(),
                                                        plan.warnings())))));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> start(UUID projectId, UUID rolloutId, EventContext context) {
    OffsetDateTime now = now();
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout -> {
              if (!"DRAFT".equals(rollout.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Rollout must be in DRAFT status to start"));
              }
              return gatewayGroupRepository
                  .findById(projectId, rollout.gatewayGroupId())
                  .flatMap(
                      group ->
                          gatewayGroupRepository
                              .hasActiveRollout(group.id())
                              .flatMap(
                                  active -> {
                                    if (active) {
                                      return Mono.error(
                                          ControlPlaneException.conflict(
                                              "Gateway group already has an active rollout"));
                                    }
                                    return rolloutRepository
                                        .listStagesByRollout(rolloutId)
                                        .map(this::toStageInput)
                                        .collectList()
                                        .flatMap(
                                            stages ->
                                                buildPlan(
                                                        group,
                                                        rollout.sourceVersion(),
                                                        rollout.targetVersion(),
                                                        rollout.strategy(),
                                                        stages,
                                                        rollout.id())
                                                    .flatMap(
                                                        plan -> {
                                                          if (plan.eligibleCount() == 0) {
                                                            return Mono.error(
                                                                ControlPlaneException
                                                                    .invalidRequest(
                                                                        "No eligible gateways for rollout"));
                                                          }
                                                          if (plan.incompatibleCount() > 0) {
                                                            return Mono.error(
                                                                ControlPlaneException
                                                                    .invalidRequest(
                                                                        "Incompatible gateways block rollout start"));
                                                          }
                                                          return persistMembershipSnapshot(
                                                                  rollout, plan, now)
                                                              .then(
                                                                  activateFirstStage(
                                                                      rollout, plan, now, context));
                                                        }));
                                  }));
            });
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> pause(UUID projectId, UUID rolloutId, EventContext context) {
    return transitionRollout(
        projectId,
        rolloutId,
        "RUNNING",
        "PAUSED",
        PlatformEventTypes.RUNTIME_ROLLOUT_PAUSED,
        context,
        rollout ->
            rolloutRepository.updateRolloutFields(
                rollout.id(), rollout.version(), now(), null, null, now(), null, "PAUSED"));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> resume(UUID projectId, UUID rolloutId, EventContext context) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout -> {
              if (!"PAUSED".equals(rollout.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Rollout must be PAUSED to resume"));
              }
              long accumulated = rollout.pauseAccumulatedMs();
              if (rollout.pausedAt() != null) {
                accumulated += Duration.between(rollout.pausedAt(), now()).toMillis();
              }
              return rolloutRepository
                  .updateRolloutFields(
                      rollout.id(),
                      rollout.version(),
                      now(),
                      null,
                      null,
                      null,
                      accumulated,
                      "RUNNING")
                  .switchIfEmpty(Mono.error(ControlPlaneException.conflict("Resume conflict")))
                  .flatMap(
                      updated ->
                          recordRolloutEvent(
                                  updated, PlatformEventTypes.RUNTIME_ROLLOUT_RESUMED, context)
                              .thenReturn(RuntimeRolloutView.from(updated)));
            });
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> advance(UUID projectId, UUID rolloutId, EventContext context) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout -> {
              if (!"RUNNING".equals(rollout.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Rollout must be RUNNING to advance"));
              }
              if (!"MANUAL".equals(rollout.progressionMode())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "Advance is only supported for MANUAL progression mode"));
              }
              return completeCurrentStageForManualAdvance(rollout, context)
                  .flatMap(updated -> advanceToNextStage(updated, context))
                  .then(rolloutRepository.findById(projectId, rolloutId))
                  .map(RuntimeRolloutView::from);
            });
  }

  private Mono<RuntimeRolloutEntity> completeCurrentStageForManualAdvance(
      RuntimeRolloutEntity rollout, EventContext context) {
    if (rollout.currentStageIndex() < 0) {
      return Mono.just(rollout);
    }
    return rolloutRepository
        .findStage(rollout.id(), rollout.currentStageIndex())
        .flatMap(
            stage -> {
              if ("SUCCEEDED".equals(stage.status()) || "SKIPPED".equals(stage.status())) {
                return rolloutRepository.findById(rollout.projectId(), rollout.id());
              }
              if ("PENDING".equals(stage.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Current stage has not been activated yet"));
              }
              if ("FAILED".equals(stage.status()) || "CANCELLED".equals(stage.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Current stage cannot be advanced"));
              }
              OffsetDateTime now = now();
              return markCurrentStageSucceeded(rollout, stage, now, context)
                  .flatMap(
                      ignored -> rolloutRepository.findById(rollout.projectId(), rollout.id()));
            })
        .switchIfEmpty(Mono.just(rollout));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> cancel(UUID projectId, UUID rolloutId, EventContext context) {
    return transitionRollout(
        projectId,
        rolloutId,
        List.of("DRAFT", "RUNNING", "PAUSED"),
        "CANCELLED",
        PlatformEventTypes.RUNTIME_ROLLOUT_CANCELLED,
        context,
        rollout ->
            rolloutRepository.updateRolloutStatus(
                rollout.id(), rollout.status(), "CANCELLED", rollout.version(), now(), null, null));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<RuntimeRolloutView> rollback(UUID projectId, UUID rolloutId, EventContext context) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout -> {
              if (!Set.of("RUNNING", "PAUSED", "FAILED").contains(rollout.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Rollout cannot be rolled back from status"));
              }
              OffsetDateTime now = now();
              return rolloutRepository
                  .updateRolloutStatus(
                      rollout.id(),
                      rollout.status(),
                      "ROLLING_BACK",
                      rollout.version(),
                      now,
                      null,
                      null)
                  .switchIfEmpty(Mono.error(ControlPlaneException.conflict("Rollback conflict")))
                  .flatMap(
                      updated ->
                          assignRollback(updated, now)
                              .then(
                                  recordRolloutEvent(
                                      updated,
                                      PlatformEventTypes.RUNTIME_ROLLOUT_ROLLBACK_STARTED,
                                      context))
                              .thenReturn(RuntimeRolloutView.from(updated)));
            });
  }

  public Mono<RuntimeRolloutDetailView> getDetail(UUID projectId, UUID rolloutId) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout ->
                rolloutRepository
                    .listStagesByRollout(rolloutId)
                    .collectList()
                    .zipWith(rolloutRepository.countAssignmentsByStatus(rolloutId))
                    .map(
                        tuple ->
                            RuntimeRolloutDetailView.from(
                                rollout, tuple.getT1(), tuple.getT2(), now())));
  }

  public Flux<RuntimeRolloutView> list(
      UUID projectId,
      UUID gatewayGroupId,
      String status,
      String strategy,
      Long sourceVersion,
      Long targetVersion,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      int limit,
      int offset) {
    int effectiveLimit = limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, 200);
    return projectService
        .get(projectId)
        .thenMany(
            rolloutRepository.listByProject(
                projectId,
                gatewayGroupId,
                status,
                strategy,
                sourceVersion,
                targetVersion,
                createdAfter,
                createdBefore,
                effectiveLimit,
                Math.max(offset, 0)))
        .map(RuntimeRolloutView::from);
  }

  public Flux<RuntimeRolloutGatewayEntity> listAssignments(
      UUID projectId, UUID rolloutId, int limit, int offset) {
    return requireRollout(projectId, rolloutId)
        .thenMany(
            rolloutRepository.listGatewaysByRollout(
                rolloutId,
                limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, 200),
                Math.max(offset, 0)));
  }

  public Mono<Void> advanceToNextStage(RuntimeRolloutEntity rollout, EventContext context) {
    return rolloutRepository
        .findById(rollout.projectId(), rollout.id())
        .flatMap(
            fresh -> {
              int nextStageIndex = fresh.currentStageIndex() + 1;
              return rolloutRepository
                  .findStage(fresh.id(), nextStageIndex)
                  .flatMap(
                      nextStage ->
                          rolloutRepository
                              .listGatewaysByRollout(fresh.id(), 10_000, 0)
                              .collectList()
                              .flatMap(
                                  assignments ->
                                      activateStage(fresh, nextStage, assignments, context)
                                          .thenReturn(Boolean.TRUE)))
                  .switchIfEmpty(
                      finalizeSuccessfulRollout(fresh, context).thenReturn(Boolean.FALSE));
            })
        .then();
  }

  Mono<Void> activateStage(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutStageEntity stage,
      List<RuntimeRolloutGatewayEntity> assignments,
      EventContext context) {
    OffsetDateTime now = now();
    List<String> cohort =
        selectStageCohort(
            stage, assignments.stream().filter(RuntimeRolloutGatewayEntity::eligible).toList());
    Mono<Void> assignGateways =
        Flux.fromIterable(cohort)
            .concatMap(
                gatewayId ->
                    rolloutRepository
                        .findGatewayAssignment(rollout.id(), gatewayId)
                        .flatMap(
                            assignment ->
                                rolloutRepository.updateGatewayAssignment(
                                    rollout.id(),
                                    gatewayId,
                                    assignment.status(),
                                    "ASSIGNED",
                                    assignment.version(),
                                    now,
                                    stage.stageIndex(),
                                    rollout.targetVersion(),
                                    assignment.assignmentGeneration() + 1,
                                    null,
                                    now,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null)))
            .then();
    Mono<Void> ensureRolloutIndex =
        rolloutRepository
            .findById(rollout.projectId(), rollout.id())
            .flatMap(
                fresh -> {
                  if (fresh.currentStageIndex() == stage.stageIndex()) {
                    return Mono.just(fresh);
                  }
                  return rolloutRepository
                      .updateRolloutFields(
                          fresh.id(),
                          fresh.version(),
                          now,
                          stage.stageIndex(),
                          null,
                          null,
                          null,
                          null)
                      .switchIfEmpty(
                          Mono.error(
                              ControlPlaneException.conflict("Stage index update conflict")));
                })
            .then();
    if ("RUNNING".equals(stage.status())) {
      return ensureRolloutIndex
          .then(assignGateways)
          .then(
              recordStageEvent(
                  rollout,
                  stage.stageIndex(),
                  PlatformEventTypes.RUNTIME_ROLLOUT_STAGE_STARTED,
                  context));
    }
    return rolloutRepository
        .updateStageStatus(
            stage.id(),
            "PENDING",
            "RUNNING",
            stage.version(),
            now,
            now,
            null,
            null,
            null,
            null,
            null)
        .switchIfEmpty(Mono.error(ControlPlaneException.conflict("Stage activation conflict")))
        .then(ensureRolloutIndex)
        .then(assignGateways)
        .then(
            recordStageEvent(
                rollout,
                stage.stageIndex(),
                PlatformEventTypes.RUNTIME_ROLLOUT_STAGE_STARTED,
                context));
  }

  private Mono<Void> markCurrentStageSucceeded(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutStageEntity stage,
      OffsetDateTime now,
      EventContext context) {
    return rolloutRepository
        .updateStageStatus(
            stage.id(),
            stage.status(),
            "SUCCEEDED",
            stage.version(),
            now,
            null,
            null,
            now,
            null,
            null,
            null)
        .switchIfEmpty(
            rolloutRepository
                .findStage(rollout.id(), stage.stageIndex())
                .flatMap(
                    reloaded -> {
                      if ("SUCCEEDED".equals(reloaded.status())
                          || "SKIPPED".equals(reloaded.status())) {
                        return Mono.just(reloaded);
                      }
                      if (!Set.of("RUNNING", "OBSERVING").contains(reloaded.status())) {
                        return Mono.error(
                            ControlPlaneException.conflict("Current stage cannot be advanced"));
                      }
                      return rolloutRepository.updateStageStatus(
                          reloaded.id(),
                          reloaded.status(),
                          "SUCCEEDED",
                          reloaded.version(),
                          now,
                          null,
                          null,
                          now,
                          null,
                          null,
                          null);
                    })
                .switchIfEmpty(
                    Mono.error(ControlPlaneException.conflict("Current stage advance conflict"))))
        .flatMap(
            ignored ->
                recordStageEvent(
                    rollout,
                    stage.stageIndex(),
                    PlatformEventTypes.RUNTIME_ROLLOUT_STAGE_SUCCEEDED,
                    context));
  }

  private Mono<Void> finalizeSuccessfulRollout(RuntimeRolloutEntity rollout, EventContext context) {
    OffsetDateTime now = now();
    return rolloutRepository
        .findById(rollout.projectId(), rollout.id())
        .flatMap(
            fresh ->
                gatewayGroupRepository
                    .findById(fresh.projectId(), fresh.gatewayGroupId())
                    .flatMap(
                        group ->
                            gatewayGroupRepository
                                .setDesiredConfigVersion(
                                    group.id(), fresh.targetVersion(), group.version(), now)
                                .then(
                                    rolloutRepository.updateRolloutStatus(
                                        fresh.id(),
                                        "RUNNING",
                                        "SUCCEEDED",
                                        fresh.version(),
                                        now,
                                        null,
                                        null))
                                .switchIfEmpty(
                                    Mono.error(
                                        ControlPlaneException.conflict(
                                            "Rollout finalize conflict")))
                                .then(
                                    recordRolloutEvent(
                                        fresh,
                                        PlatformEventTypes.RUNTIME_ROLLOUT_SUCCEEDED,
                                        context))));
  }

  public Mono<Void> initiateRollbackAssignments(RuntimeRolloutEntity rollout) {
    return assignRollback(rollout, now());
  }

  public Mono<Void> recordRolloutEventPublic(
      RuntimeRolloutEntity rollout, String eventType, EventContext context) {
    return recordRolloutEvent(rollout, eventType, context);
  }

  public Mono<Void> recordStageEventPublic(
      RuntimeRolloutEntity rollout, int stageIndex, String eventType, EventContext context) {
    return recordStageEvent(rollout, stageIndex, eventType, context);
  }

  private Mono<RuntimeRolloutView> activateFirstStage(
      RuntimeRolloutEntity rollout, RolloutPlan plan, OffsetDateTime now, EventContext context) {
    return rolloutRepository
        .updateRolloutFields(rollout.id(), rollout.version(), now, 0, now, null, null, "RUNNING")
        .switchIfEmpty(Mono.error(ControlPlaneException.conflict("Rollout start conflict")))
        .flatMap(
            running ->
                rolloutRepository
                    .findStage(running.id(), 0)
                    .flatMap(
                        stage ->
                            rolloutRepository
                                .listGatewaysByRollout(running.id(), 10_000, 0)
                                .collectList()
                                .flatMap(
                                    assignments ->
                                        activateStage(running, stage, assignments, context)
                                            .then(
                                                recordRolloutEvent(
                                                    running,
                                                    PlatformEventTypes.RUNTIME_ROLLOUT_STARTED,
                                                    context))
                                            .thenReturn(RuntimeRolloutView.from(running)))));
  }

  private Mono<Void> persistMembershipSnapshot(
      RuntimeRolloutEntity rollout, RolloutPlan plan, OffsetDateTime now) {
    return Flux.fromIterable(plan.members())
        .concatMap(
            member ->
                rolloutRepository.insertGatewayAssignment(
                    rollout.id(),
                    member.gatewayId(),
                    member.cohortRank(),
                    null,
                    member.previousDesiredVersion(),
                    member.eligible() ? rollout.targetVersion() : null,
                    0L,
                    member.eligible() ? "PENDING" : "EXCLUDED",
                    member.eligible(),
                    member.exclusionReason(),
                    now))
        .then();
  }

  private Mono<Void> assignRollback(RuntimeRolloutEntity rollout, OffsetDateTime now) {
    return rolloutRepository
        .listGatewaysByRollout(rollout.id(), 10_000, 0)
        .filter(
            assignment ->
                assignment.eligible()
                    && Set.of(
                            "ASSIGNED",
                            "DELIVERED",
                            "ACKNOWLEDGED",
                            "ACTIVATED",
                            "FAILED",
                            "TIMED_OUT")
                        .contains(assignment.status()))
        .concatMap(
            assignment ->
                rolloutRepository.updateGatewayAssignment(
                    rollout.id(),
                    assignment.gatewayId(),
                    assignment.status(),
                    "ROLLBACK_ASSIGNED",
                    assignment.version(),
                    now,
                    assignment.assignedStageIndex(),
                    rollout.sourceVersion(),
                    null,
                    assignment.rollbackGeneration() + 1,
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .then();
  }

  private Mono<RolloutPlan> buildPlan(
      GatewayGroupEntity group,
      long sourceVersion,
      long targetVersion,
      String strategy,
      List<StageDefinitionInput> stages,
      UUID rolloutId) {
    RolloutStageCalculator.validateStageDefinitions(stages, properties.maxStages());
    OffsetDateTime staleCutoff = now().minus(controlPlaneProperties.gatewayStaleAfter());
    return resolveMembershipContext(group.projectId(), group.apiId())
        .flatMap(
            ctx ->
                rolloutRepository
                    .listGatewayPlacements()
                    .collectList()
                    .map(
                        placements -> {
                          List<PlanMember> members = new ArrayList<>();
                          int excluded = 0;
                          int offline = 0;
                          int incompatible = 0;
                          List<String> warnings = new ArrayList<>();
                          for (GatewayPlacementRow placement : placements) {
                            ResolvedMember resolved =
                                resolveMember(
                                    placement,
                                    group.id(),
                                    ctx,
                                    staleCutoff,
                                    sourceVersion,
                                    targetVersion);
                            if (resolved.inGroup()) {
                              members.add(
                                  new PlanMember(
                                      placement.gatewayId(),
                                      RolloutCohortRanker.rank(rolloutId, placement.gatewayId()),
                                      resolved.eligible(),
                                      resolved.exclusionReason(),
                                      resolved.previousDesiredVersion()));
                              if (!resolved.eligible()) {
                                excluded++;
                                if ("offline".equals(resolved.exclusionReason())) {
                                  offline++;
                                } else if ("incompatible".equals(resolved.exclusionReason())) {
                                  incompatible++;
                                }
                              }
                            }
                          }
                          members.sort(
                              Comparator.comparingLong(PlanMember::cohortRank)
                                  .thenComparing(PlanMember::gatewayId));
                          List<RolloutCohortRanker.RankedGateway> ranked =
                              members.stream()
                                  .filter(PlanMember::eligible)
                                  .map(
                                      m ->
                                          new RolloutCohortRanker.RankedGateway(
                                              m.gatewayId(), m.cohortRank()))
                                  .toList();
                          int eligibleCount = ranked.size();
                          List<StageCount> stageCounts = new ArrayList<>();
                          Set<String> cumulative = new HashSet<>();
                          for (int i = 0; i < stages.size(); i++) {
                            StageDefinitionInput stage = stages.get(i);
                            int count =
                                RolloutStageCalculator.stageGatewayCount(
                                    eligibleCount,
                                    stage.percentage(),
                                    stage.minimumGatewayCount(),
                                    stage.maximumGatewayCount());
                            List<String> cohort =
                                RolloutStageCalculator.selectCohort(ranked, count);
                            cumulative.addAll(cohort);
                            stageCounts.add(new StageCount(i, stage.percentage(), count));
                          }
                          List<String> sample =
                              ranked.stream()
                                  .limit(MAX_PREVIEW_SAMPLE)
                                  .map(RolloutCohortRanker.RankedGateway::gatewayId)
                                  .toList();
                          if (eligibleCount == 0) {
                            warnings.add("No eligible gateways matched the group");
                          }
                          return new RolloutPlan(
                              members,
                              eligibleCount,
                              excluded,
                              offline,
                              incompatible,
                              stageCounts,
                              sample,
                              warnings);
                        }));
  }

  private List<String> selectStageCohort(
      RuntimeRolloutStageEntity stage, List<RuntimeRolloutGatewayEntity> eligibleAssignments) {
    List<RolloutCohortRanker.RankedGateway> ranked =
        eligibleAssignments.stream()
            .map(a -> new RolloutCohortRanker.RankedGateway(a.gatewayId(), a.cohortRank()))
            .sorted(
                Comparator.comparingLong(RolloutCohortRanker.RankedGateway::cohortRank)
                    .thenComparing(RolloutCohortRanker.RankedGateway::gatewayId))
            .toList();
    int count =
        RolloutStageCalculator.stageGatewayCount(
            ranked.size(),
            stage.percentage(),
            stage.minimumGatewayCount(),
            stage.maximumGatewayCount());
    return RolloutStageCalculator.selectCohort(ranked, count);
  }

  private Mono<Void> insertStageDefinitions(
      UUID rolloutId, List<StageDefinitionInput> stages, OffsetDateTime now) {
    return Flux.range(0, stages.size())
        .concatMap(
            index -> {
              StageDefinitionInput stage = stages.get(index);
              return rolloutRepository.insertStage(
                  UUID.randomUUID(),
                  rolloutId,
                  index,
                  stage.percentage(),
                  stage.minimumGatewayCount(),
                  stage.maximumGatewayCount(),
                  stage.requiredConvergedPercentage(),
                  stage.maximumFailedGateways(),
                  stage.maximumTimedOutGateways(),
                  stage.requiredOnlinePercentage(),
                  stage.observationDurationMs(),
                  stage.stageTimeoutMs(),
                  "PENDING",
                  now);
            })
        .then();
  }

  private Mono<RuntimeRolloutEntity> requireRollout(UUID projectId, UUID rolloutId) {
    return rolloutRepository
        .findById(projectId, rolloutId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Rollout was not found")));
  }

  private Mono<GatewayGroupEntity> requireGroup(UUID projectId, UUID groupId) {
    return gatewayGroupRepository
        .findById(projectId, groupId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway group was not found")));
  }

  private Mono<Long> resolveSourceVersion(GatewayGroupEntity group) {
    if (group.desiredConfigVersion() != null) {
      return Mono.just(group.desiredConfigVersion());
    }
    return apiDefinitionService
        .get(group.apiId())
        .flatMap(
            api -> {
              if (api.desiredConfigVersion() == null) {
                return Mono.error(
                    ControlPlaneException.desiredConfigNotSet("Source version not set"));
              }
              return Mono.just(api.desiredConfigVersion());
            });
  }

  private Mono<Void> validateVersions(UUID apiId, long targetVersion) {
    return configVersionRepository
        .findByApiIdAndVersion(apiId, targetVersion)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.configVersionNotFound("Target version was not found")))
        .then();
  }

  private Mono<RuntimeRolloutView> transitionRollout(
      UUID projectId,
      UUID rolloutId,
      String expectedStatus,
      String newStatus,
      String eventType,
      EventContext context,
      java.util.function.Function<RuntimeRolloutEntity, Mono<RuntimeRolloutEntity>> update) {
    return transitionRollout(
        projectId, rolloutId, List.of(expectedStatus), newStatus, eventType, context, update);
  }

  private Mono<RuntimeRolloutView> transitionRollout(
      UUID projectId,
      UUID rolloutId,
      List<String> expectedStatuses,
      String newStatus,
      String eventType,
      EventContext context,
      java.util.function.Function<RuntimeRolloutEntity, Mono<RuntimeRolloutEntity>> update) {
    return requireRollout(projectId, rolloutId)
        .flatMap(
            rollout -> {
              if (!expectedStatuses.contains(rollout.status())) {
                return Mono.error(
                    ControlPlaneException.conflict("Invalid rollout status transition"));
              }
              return update
                  .apply(rollout)
                  .switchIfEmpty(
                      Mono.error(ControlPlaneException.conflict("Rollout update conflict")))
                  .flatMap(
                      updated ->
                          recordRolloutEvent(updated, eventType, context)
                              .thenReturn(RuntimeRolloutView.from(updated)));
            });
  }

  private Mono<MembershipContext> resolveMembershipContext(UUID projectId, UUID apiId) {
    return gatewayGroupRepository
        .listEnabledByProject(projectId)
        .filter(group -> group.apiId().equals(apiId))
        .map(this::toGroupContext)
        .collectList()
        .zipWith(gatewayGroupRepository.listMembershipsByProject(projectId).collectList())
        .map(
            tuple -> {
              Map<String, GatewayMembershipResolver.ExplicitMembership> explicitByGateway =
                  new HashMap<>();
              for (GatewayGroupMembershipRow membership : tuple.getT2()) {
                explicitByGateway.put(
                    membership.gatewayId(),
                    new GatewayMembershipResolver.ExplicitMembership(
                        membership.gatewayGroupId(),
                        parseMembershipKind(membership.membershipType())));
              }
              return new MembershipContext(tuple.getT1(), explicitByGateway);
            });
  }

  private ResolvedMember resolveMember(
      GatewayPlacementRow placement,
      UUID groupId,
      MembershipContext ctx,
      OffsetDateTime staleCutoff,
      long sourceVersion,
      long targetVersion) {
    Map<String, String> effectiveLabels =
        GatewayLabelValidator.mergeGatewayLabels(
            GatewayMembershipResolver.parseLabels(objectMapper, placement.adminLabelsJson()),
            GatewayMembershipResolver.parseLabels(objectMapper, placement.labelsJson()));
    GatewayMembershipResolver.ResolvedMembership resolved =
        GatewayMembershipResolver.resolve(
            placement.gatewayId(), effectiveLabels, ctx.groups(), ctx.explicitByGateway());
    boolean inGroup = groupId.equals(resolved.gatewayGroupId());
    if (!inGroup) {
      return new ResolvedMember(false, false, null, null);
    }
    if (placement.lastSeenAt() == null || placement.lastSeenAt().isBefore(staleCutoff)) {
      return new ResolvedMember(true, false, "offline", sourceVersion);
    }
    if (placement.runtimeSchemaVersion() == null) {
      return new ResolvedMember(true, false, "incompatible", sourceVersion);
    }
    return new ResolvedMember(true, true, null, sourceVersion);
  }

  private GatewayMembershipResolver.GroupMembershipContext toGroupContext(
      GatewayGroupEntity group) {
    return new GatewayMembershipResolver.GroupMembershipContext(
        group.id(),
        group.projectId(),
        group.apiId(),
        group.name(),
        group.enabled(),
        parseSelector(group.selectorJson()),
        group.desiredConfigVersion());
  }

  private List<StageDefinitionInput> effectiveStages(
      String strategy, List<StageDefinitionInput> stages) {
    if ("ALL_AT_ONCE".equals(strategy)) {
      return List.of(
          new StageDefinitionInput(
              100,
              1,
              null,
              properties.defaultRequiredConvergedPercentage(),
              0,
              0,
              0,
              properties.defaultObservationDuration().toMillis(),
              properties.defaultStageTimeout().toMillis()));
    }
    if (stages == null || stages.isEmpty()) {
      throw ControlPlaneException.invalidRequest("Stages are required for progressive rollout");
    }
    return stages;
  }

  private StageDefinitionInput toStageInput(RuntimeRolloutStageEntity stage) {
    return new StageDefinitionInput(
        stage.percentage(),
        stage.minimumGatewayCount(),
        stage.maximumGatewayCount(),
        stage.requiredConvergedPercentage(),
        stage.maximumFailedGateways(),
        stage.maximumTimedOutGateways(),
        stage.requiredOnlinePercentage(),
        stage.observationDurationMs(),
        stage.stageTimeoutMs());
  }

  private Mono<Void> recordRolloutEvent(
      RuntimeRolloutEntity rollout, String eventType, EventContext context) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType,
                rollout.projectId(),
                rollout.apiId(),
                "RUNTIME_ROLLOUT",
                rollout.id().toString(),
                context,
                Map.of(
                    "rolloutId", rollout.id().toString(),
                    "gatewayGroupId", rollout.gatewayGroupId().toString(),
                    "status", rollout.status(),
                    "strategy", rollout.strategy(),
                    "sourceVersion", rollout.sourceVersion(),
                    "targetVersion", rollout.targetVersion())))
        .then();
  }

  private Mono<Void> recordStageEvent(
      RuntimeRolloutEntity rollout, int stageIndex, String eventType, EventContext context) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType,
                rollout.projectId(),
                rollout.apiId(),
                "RUNTIME_ROLLOUT",
                rollout.id().toString(),
                context,
                Map.of(
                    "rolloutId", rollout.id().toString(),
                    "stageIndex", stageIndex,
                    "strategy", rollout.strategy())))
        .then();
  }

  private JsonNode parseSelector(String selectorJson) {
    try {
      return objectMapper.readTree(selectorJson == null ? "{}" : selectorJson);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private static GatewayMembershipResolver.MembershipKind parseMembershipKind(String type) {
    return switch (type) {
      case "EXPLICIT_INCLUDE" -> GatewayMembershipResolver.MembershipKind.EXPLICIT_INCLUDE;
      case "EXPLICIT_EXCLUDE" -> GatewayMembershipResolver.MembershipKind.EXPLICIT_EXCLUDE;
      default -> GatewayMembershipResolver.MembershipKind.SELECTOR;
    };
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
  }

  private record MembershipContext(
      List<GatewayMembershipResolver.GroupMembershipContext> groups,
      Map<String, GatewayMembershipResolver.ExplicitMembership> explicitByGateway) {}

  private record ResolvedMember(
      boolean inGroup, boolean eligible, String exclusionReason, Long previousDesiredVersion) {}

  private record PlanMember(
      String gatewayId,
      long cohortRank,
      boolean eligible,
      String exclusionReason,
      Long previousDesiredVersion) {}

  private record StageCount(int stageIndex, int percentage, int gatewayCount) {}

  private record RolloutPlan(
      List<PlanMember> members,
      int eligibleCount,
      int excludedCount,
      int offlineCount,
      int incompatibleCount,
      List<StageCount> stageCounts,
      List<String> sampleGatewayIds,
      List<String> warnings) {}

  public record RuntimeRolloutView(
      UUID id,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      long sourceVersion,
      long targetVersion,
      String strategy,
      String progressionMode,
      String status,
      int currentStageIndex,
      UUID correlationId,
      OffsetDateTime startedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      long version) {

    static RuntimeRolloutView from(RuntimeRolloutEntity entity) {
      return new RuntimeRolloutView(
          entity.id(),
          entity.projectId(),
          entity.gatewayGroupId(),
          entity.apiId(),
          entity.sourceVersion(),
          entity.targetVersion(),
          entity.strategy(),
          entity.progressionMode(),
          entity.status(),
          entity.currentStageIndex(),
          entity.correlationId(),
          entity.startedAt(),
          entity.createdAt(),
          entity.updatedAt(),
          entity.version());
    }
  }

  public record RolloutPreview(
      UUID gatewayGroupId,
      long sourceVersion,
      long targetVersion,
      int eligibleGatewayCount,
      int excludedGatewayCount,
      int offlineGatewayCount,
      int incompatibleGatewayCount,
      List<StageCount> stageGatewayCounts,
      List<String> sampleGatewayIds,
      List<String> warnings) {}

  public record RuntimeRolloutDetailView(
      RuntimeRolloutView rollout,
      List<RuntimeRolloutStageEntity> stages,
      Map<String, Long> assignmentCountsByStatus,
      long activeElapsedMs) {

    static RuntimeRolloutDetailView from(
        RuntimeRolloutEntity rollout,
        List<RuntimeRolloutStageEntity> stages,
        Map<String, Long> assignmentCounts,
        OffsetDateTime now) {
      long elapsed = 0L;
      if (rollout.startedAt() != null) {
        elapsed =
            Duration.between(rollout.startedAt(), now).toMillis() - rollout.pauseAccumulatedMs();
        if (rollout.pausedAt() != null) {
          elapsed -= Duration.between(rollout.pausedAt(), now).toMillis();
        }
        elapsed = Math.max(elapsed, 0L);
      }
      return new RuntimeRolloutDetailView(
          RuntimeRolloutView.from(rollout), stages, assignmentCounts, elapsed);
    }
  }
}
