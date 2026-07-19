package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.persistence.RuntimeRolloutGatewayEntity;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Updates rollout assignment state from authenticated gateway config-status reports. */
@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class RolloutAssignmentAckService {

  private final RuntimeRolloutRepositoryCustom rolloutRepository;
  private final Clock clock;

  public RolloutAssignmentAckService(
      RuntimeRolloutRepositoryCustom rolloutRepository, Clock eventsClock) {
    this.rolloutRepository = rolloutRepository;
    this.clock = eventsClock;
  }

  public Mono<Boolean> processReport(
      String gatewayId,
      RolloutConfigStatusReport report,
      boolean activationSucceeded,
      String errorCode,
      String errorSummary) {
    if (report.rolloutId() == null || report.assignmentGeneration() == null) {
      return Mono.just(false);
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    return rolloutRepository
        .findGatewayAssignment(report.rolloutId(), gatewayId)
        .flatMap(
            assignment -> {
              if (isStaleGeneration(report, assignment)) {
                return Mono.just(true);
              }
              String nextStatus = deriveStatus(assignment, report, activationSucceeded);
              if (nextStatus == null) {
                return Mono.just(true);
              }
              OffsetDateTime acknowledgedAt =
                  "ACKNOWLEDGED".equals(nextStatus) ? now : assignment.acknowledgedAt();
              OffsetDateTime activatedAt =
                  "ACTIVATED".equals(nextStatus) ? now : assignment.activatedAt();
              OffsetDateTime failedAt = "FAILED".equals(nextStatus) ? now : assignment.failedAt();
              OffsetDateTime rolledBackAt =
                  "ROLLED_BACK".equals(nextStatus) ? now : assignment.rolledBackAt();
              return rolloutRepository
                  .updateGatewayAssignment(
                      report.rolloutId(),
                      gatewayId,
                      assignment.status(),
                      nextStatus,
                      assignment.version(),
                      now,
                      assignment.assignedStageIndex(),
                      assignment.targetDesiredVersion(),
                      assignment.assignmentGeneration(),
                      assignment.rollbackGeneration(),
                      assignment.assignedAt(),
                      acknowledgedAt,
                      activatedAt,
                      failedAt,
                      assignment.timedOutAt(),
                      rolledBackAt,
                      report.version(),
                      errorCode,
                      errorSummary)
                  .thenReturn(true);
            })
        .defaultIfEmpty(false);
  }

  private static boolean isStaleGeneration(
      RolloutConfigStatusReport report, RuntimeRolloutGatewayEntity assignment) {
    if ("ROLLBACK_ASSIGNED".equals(assignment.status())) {
      return report.assignmentGeneration() < assignment.rollbackGeneration();
    }
    return report.assignmentGeneration() < assignment.assignmentGeneration();
  }

  private static String deriveStatus(
      RuntimeRolloutGatewayEntity assignment,
      RolloutConfigStatusReport report,
      boolean activationSucceeded) {
    if ("ROLLBACK_ASSIGNED".equals(assignment.status())) {
      if (!activationSucceeded) {
        return "ROLLBACK_FAILED";
      }
      if (report.version() == assignment.previousDesiredVersion()) {
        return "ROLLED_BACK";
      }
      return null;
    }
    if (!activationSucceeded) {
      return "FAILED";
    }
    if ("ACK".equals(report.status()) && report.version() == assignment.targetDesiredVersion()) {
      return "ACTIVATED";
    }
    if ("ACK".equals(report.status())) {
      return "ACKNOWLEDGED";
    }
    return "FAILED";
  }

  public record RolloutConfigStatusReport(
      UUID rolloutId, Long assignmentGeneration, long version, String status) {}
}
