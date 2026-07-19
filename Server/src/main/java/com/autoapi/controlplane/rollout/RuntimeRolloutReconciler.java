package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.persistence.RuntimeRolloutEntity;
import com.autoapi.controlplane.persistence.RuntimeRolloutGatewayEntity;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import com.autoapi.controlplane.persistence.RuntimeRolloutStageEntity;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Scheduled worker that evaluates active rollout stages across control-plane replicas. */
public final class RuntimeRolloutReconciler {

  private static final Logger log = LoggerFactory.getLogger(RuntimeRolloutReconciler.class);
  private static final Set<String> CONVERGED_STATUSES = Set.of("ACTIVATED");
  private static final Set<String> FAILED_STATUSES = Set.of("FAILED");
  private static final Set<String> TIMED_OUT_STATUSES = Set.of("TIMED_OUT");

  private final Disposable subscription;

  public RuntimeRolloutReconciler(
      RolloutsProperties properties,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      ControlPlaneProperties controlPlaneProperties,
      RolloutsMetrics metrics,
      Clock clock) {
    if (!properties.enabled()) {
      this.subscription = null;
      return;
    }
    subscription =
        Flux.interval(properties.reconciler().pollInterval())
            .concatMap(
                tick ->
                    rolloutRepository
                        .claimActiveRollouts(properties.reconciler().batchSize(), now(clock))
                        .concatMap(
                            rollout ->
                                reconcileRollout(
                                    rollout,
                                    rolloutRepository,
                                    rolloutService,
                                    controlPlaneProperties,
                                    metrics,
                                    clock))
                        .then()
                        .doOnSubscribe(ignored -> metrics.recordReconcilerRun()))
            .onErrorContinue(
                (error, obj) -> {
                  metrics.recordReconcilerFailure();
                  log.warn("Runtime rollout reconciler iteration failed: {}", error.getMessage());
                })
            .subscribe();
  }

  @PreDestroy
  void shutdown() {
    if (subscription != null) {
      subscription.dispose();
    }
  }

  private static Mono<Void> reconcileRollout(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      ControlPlaneProperties controlPlaneProperties,
      RolloutsMetrics metrics,
      Clock clock) {
    if ("PAUSED".equals(rollout.status())) {
      return Mono.empty();
    }
    if ("ROLLING_BACK".equals(rollout.status())) {
      return evaluateRollbackCompletion(rollout, rolloutRepository, metrics, clock);
    }
    if (!"RUNNING".equals(rollout.status())) {
      return Mono.empty();
    }
    if (rollout.currentStageIndex() < 0) {
      return Mono.empty();
    }
    return rolloutRepository
        .findStage(rollout.id(), rollout.currentStageIndex())
        .flatMap(
            stage ->
                switch (stage.status()) {
                  case "RUNNING" ->
                      evaluateRunningStage(
                          rollout,
                          stage,
                          rolloutRepository,
                          rolloutService,
                          controlPlaneProperties,
                          metrics,
                          clock);
                  case "OBSERVING" ->
                      evaluateObservingStage(
                          rollout, stage, rolloutRepository, rolloutService, metrics, clock);
                  default -> Mono.empty();
                });
  }

  private static Mono<Void> evaluateRunningStage(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutStageEntity stage,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      ControlPlaneProperties controlPlaneProperties,
      RolloutsMetrics metrics,
      Clock clock) {
    OffsetDateTime now = now(clock);
    if (stage.startedAt() != null
        && Duration.between(stage.startedAt(), now).toMillis() > stage.stageTimeoutMs()) {
      metrics.recordGatewayTimeout(rollout.strategy());
      return failStage(
          rollout,
          stage,
          rolloutRepository,
          rolloutService,
          "STAGE_TIMEOUT",
          "Stage timed out",
          metrics,
          clock,
          rollout.autoRollbackOnFailure());
    }
    return rolloutRepository
        .listGatewaysByRollout(rollout.id(), 10_000, 0)
        .filter(
            assignment ->
                assignment.eligible()
                    && assignment.assignedStageIndex() != null
                    && assignment.assignedStageIndex() == stage.stageIndex())
        .collectList()
        .flatMap(
            cohort -> {
              StageEvaluation evaluation = evaluateCohort(cohort, controlPlaneProperties, clock);
              metrics.setGatewaysAssigned(rollout.strategy(), cohort.size());
              metrics.setGatewaysConverged(rollout.strategy(), evaluation.converged());
              if (evaluation.failed() > stage.maximumFailedGateways()
                  || evaluation.timedOut() > stage.maximumTimedOutGateways()) {
                metrics.recordGatewayFailure(rollout.strategy(), "THRESHOLD");
                return failStage(
                    rollout,
                    stage,
                    rolloutRepository,
                    rolloutService,
                    "THRESHOLD_EXCEEDED",
                    "Stage failure threshold exceeded",
                    metrics,
                    clock,
                    rollout.autoRollbackOnFailure());
              }
              if (evaluation.convergedPercent() < stage.requiredConvergedPercentage()) {
                return Mono.empty();
              }
              if (evaluation.onlinePercent() < stage.requiredOnlinePercentage()) {
                return Mono.empty();
              }
              return rolloutRepository
                  .updateStageStatus(
                      stage.id(),
                      "RUNNING",
                      "OBSERVING",
                      stage.version(),
                      now,
                      null,
                      now,
                      null,
                      null,
                      null,
                      null)
                  .flatMap(
                      updated ->
                          rolloutService
                              .recordStageEventPublic(
                                  rollout,
                                  stage.stageIndex(),
                                  PlatformEventTypes.RUNTIME_ROLLOUT_STAGE_OBSERVING,
                                  EventContext.scheduledJob(
                                      "runtime-rollout-reconciler", "RUNTIME_ROLLOUT_RECONCILER"))
                              .doOnSuccess(
                                  ignored ->
                                      metrics.recordStageTransition(
                                          rollout.strategy(), "RUNNING", "OBSERVING")));
            });
  }

  private static Mono<Void> evaluateObservingStage(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutStageEntity stage,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      RolloutsMetrics metrics,
      Clock clock) {
    OffsetDateTime now = now(clock);
    if (stage.observationStartedAt() == null) {
      return Mono.empty();
    }
    long elapsed = Duration.between(stage.observationStartedAt(), now).toMillis();
    if (elapsed < stage.observationDurationMs()) {
      return Mono.empty();
    }
    return rolloutRepository
        .updateStageStatus(
            stage.id(),
            "OBSERVING",
            "SUCCEEDED",
            stage.version(),
            now,
            null,
            null,
            now,
            null,
            null,
            null)
        .flatMap(
            updated ->
                rolloutService
                    .recordStageEventPublic(
                        rollout,
                        stage.stageIndex(),
                        PlatformEventTypes.RUNTIME_ROLLOUT_STAGE_SUCCEEDED,
                        EventContext.scheduledJob(
                            "runtime-rollout-reconciler", "RUNTIME_ROLLOUT_RECONCILER"))
                    .then(
                        handleStageSucceeded(
                            rollout, rolloutRepository, rolloutService, metrics, clock)));
  }

  private static Mono<Void> handleStageSucceeded(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      RolloutsMetrics metrics,
      Clock clock) {
    metrics.recordStageTransition(rollout.strategy(), "OBSERVING", "SUCCEEDED");
    if ("AUTOMATIC".equals(rollout.progressionMode())) {
      return rolloutService
          .advanceToNextStage(
              rollout,
              EventContext.scheduledJob("runtime-rollout-reconciler", "RUNTIME_ROLLOUT_RECONCILER"))
          .doOnSuccess(
              ignored ->
                  metrics.recordStageTransition(rollout.strategy(), "SUCCEEDED", "ADVANCED"));
    }
    return Mono.empty();
  }

  private static Mono<Void> failStage(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutStageEntity stage,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RuntimeRolloutService rolloutService,
      String failureCode,
      String failureSummary,
      RolloutsMetrics metrics,
      Clock clock,
      boolean autoRollback) {
    OffsetDateTime now = now(clock);
    return rolloutRepository
        .updateStageStatus(
            stage.id(),
            stage.status(),
            "FAILED",
            stage.version(),
            now,
            null,
            null,
            null,
            now,
            failureCode,
            failureSummary)
        .flatMap(
            ignored ->
                rolloutRepository
                    .updateRolloutStatus(
                        rollout.id(),
                        "RUNNING",
                        autoRollback ? "ROLLING_BACK" : "FAILED",
                        rollout.version(),
                        now,
                        failureCode,
                        failureSummary)
                    .flatMap(
                        updated -> {
                          metrics.recordStageTransition(
                              rollout.strategy(), stage.status(), "FAILED");
                          if (autoRollback) {
                            metrics.recordRollback(rollout.strategy(), true);
                            return rolloutService
                                .initiateRollbackAssignments(updated)
                                .then(
                                    rolloutService.recordRolloutEventPublic(
                                        updated,
                                        PlatformEventTypes.RUNTIME_ROLLOUT_ROLLBACK_STARTED,
                                        EventContext.scheduledJob(
                                            "runtime-rollout-reconciler",
                                            "RUNTIME_ROLLOUT_RECONCILER")))
                                .then();
                          }
                          metrics.recordRolloutFailure(rollout.strategy(), failureCode);
                          return rolloutService.recordRolloutEventPublic(
                              updated,
                              PlatformEventTypes.RUNTIME_ROLLOUT_FAILED,
                              EventContext.scheduledJob(
                                  "runtime-rollout-reconciler", "RUNTIME_ROLLOUT_RECONCILER"));
                        }));
  }

  private static Mono<Void> evaluateRollbackCompletion(
      RuntimeRolloutEntity rollout,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      RolloutsMetrics metrics,
      Clock clock) {
    return rolloutRepository
        .countAssignmentsByStatus(rollout.id())
        .flatMap(
            counts -> {
              long rollbackAssigned = counts.getOrDefault("ROLLBACK_ASSIGNED", 0L);
              long rolledBack = counts.getOrDefault("ROLLED_BACK", 0L);
              long rollbackFailed = counts.getOrDefault("ROLLBACK_FAILED", 0L);
              if (rollbackFailed > 0) {
                metrics.recordRollbackFailure(rollout.strategy());
                return rolloutRepository
                    .updateRolloutStatus(
                        rollout.id(),
                        "ROLLING_BACK",
                        "ROLLBACK_FAILED",
                        rollout.version(),
                        now(clock),
                        "ROLLBACK_FAILED",
                        "One or more gateways failed rollback")
                    .then();
              }
              if (rollbackAssigned == 0 || rolledBack >= rollbackAssigned) {
                metrics.recordRollback(rollout.strategy(), false);
                return rolloutRepository
                    .updateRolloutStatus(
                        rollout.id(),
                        "ROLLING_BACK",
                        "ROLLED_BACK",
                        rollout.version(),
                        now(clock),
                        null,
                        null)
                    .then();
              }
              return Mono.empty();
            });
  }

  private static StageEvaluation evaluateCohort(
      List<RuntimeRolloutGatewayEntity> cohort,
      ControlPlaneProperties controlPlaneProperties,
      Clock clock) {
    if (cohort.isEmpty()) {
      return new StageEvaluation(0, 0, 0, 0, 0, 0);
    }
    OffsetDateTime staleCutoff = now(clock).minus(controlPlaneProperties.gatewayStaleAfter());
    int converged = 0;
    int failed = 0;
    int timedOut = 0;
    int online = 0;
    for (RuntimeRolloutGatewayEntity assignment : cohort) {
      if (CONVERGED_STATUSES.contains(assignment.status())) {
        converged++;
      }
      if (FAILED_STATUSES.contains(assignment.status())) {
        failed++;
      }
      if (TIMED_OUT_STATUSES.contains(assignment.status())) {
        timedOut++;
      }
      if (assignment.assignedAt() != null && !assignment.assignedAt().isBefore(staleCutoff)) {
        online++;
      }
    }
    int total = cohort.size();
    int convergedPercent = (int) Math.floor((converged * 100.0) / total);
    int onlinePercent = (int) Math.floor((online * 100.0) / total);
    return new StageEvaluation(total, converged, failed, timedOut, convergedPercent, onlinePercent);
  }

  private static OffsetDateTime now(Clock clock) {
    return OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
  }

  private record StageEvaluation(
      int total,
      int converged,
      int failed,
      int timedOut,
      int convergedPercent,
      int onlinePercent) {}
}
