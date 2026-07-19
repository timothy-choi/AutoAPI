package com.autoapi.controlplane.persistence;

import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RuntimeRolloutRepositoryCustom {

  private final DatabaseClient databaseClient;

  public RuntimeRolloutRepositoryCustom(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RuntimeRolloutEntity> insertRollout(
      UUID id,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      long sourceVersion,
      long targetVersion,
      String strategy,
      String progressionMode,
      String status,
      String membershipMode,
      boolean autoRollbackOnFailure,
      String cancelBehavior,
      UUID correlationId,
      String createdByActorType,
      String createdByActorId,
      OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            INSERT INTO runtime_rollouts (
              id, project_id, gateway_group_id, api_id, source_version, target_version,
              strategy, progression_mode, status, current_stage_index, membership_mode,
              auto_rollback_on_failure, cancel_behavior, correlation_id,
              created_by_actor_type, created_by_actor_id, created_at, updated_at, version
            ) VALUES (
              :id, :projectId, :gatewayGroupId, :apiId, :sourceVersion, :targetVersion,
              :strategy, :progressionMode, :status, -1, :membershipMode,
              :autoRollbackOnFailure, :cancelBehavior, :correlationId,
              :createdByActorType, :createdByActorId, :createdAt, :updatedAt, 0
            )
            RETURNING id, project_id, gateway_group_id, api_id, source_version, target_version,
                      strategy, progression_mode, status, current_stage_index, membership_mode,
                      auto_rollback_on_failure, cancel_behavior, correlation_id,
                      created_by_actor_type, created_by_actor_id, started_at, paused_at,
                      pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                      failure_summary, created_at, updated_at, version
            """)
        .bind("id", id)
        .bind("projectId", projectId)
        .bind("gatewayGroupId", gatewayGroupId)
        .bind("apiId", apiId)
        .bind("sourceVersion", sourceVersion)
        .bind("targetVersion", targetVersion)
        .bind("strategy", strategy)
        .bind("progressionMode", progressionMode)
        .bind("status", status)
        .bind("membershipMode", membershipMode)
        .bind("autoRollbackOnFailure", autoRollbackOnFailure)
        .bind("cancelBehavior", cancelBehavior)
        .bind("correlationId", correlationId)
        .bind("createdByActorType", createdByActorType)
        .bind("createdByActorId", createdByActorId)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .map(this::mapRollout)
        .one();
  }

  public Mono<RuntimeRolloutStageEntity> insertStage(
      UUID id,
      UUID rolloutId,
      int stageIndex,
      int percentage,
      int minimumGatewayCount,
      Integer maximumGatewayCount,
      int requiredConvergedPercentage,
      int maximumFailedGateways,
      int maximumTimedOutGateways,
      int requiredOnlinePercentage,
      long observationDurationMs,
      long stageTimeoutMs,
      String status,
      OffsetDateTime now) {
    DatabaseClient.GenericExecuteSpec spec =
        databaseClient
            .sql(
                """
            INSERT INTO runtime_rollout_stages (
              id, rollout_id, stage_index, percentage, minimum_gateway_count,
              maximum_gateway_count, required_converged_percentage, maximum_failed_gateways,
              maximum_timed_out_gateways, required_online_percentage, observation_duration_ms,
              stage_timeout_ms, status, created_at, updated_at, version
            ) VALUES (
              :id, :rolloutId, :stageIndex, :percentage, :minimumGatewayCount,
              :maximumGatewayCount, :requiredConvergedPercentage, :maximumFailedGateways,
              :maximumTimedOutGateways, :requiredOnlinePercentage, :observationDurationMs,
              :stageTimeoutMs, :status, :createdAt, :updatedAt, 0
            )
            RETURNING id, rollout_id, stage_index, percentage, minimum_gateway_count,
                      maximum_gateway_count, required_converged_percentage,
                      maximum_failed_gateways, maximum_timed_out_gateways,
                      required_online_percentage, observation_duration_ms, stage_timeout_ms,
                      status, started_at, observation_started_at, completed_at, failed_at,
                      failure_code, failure_summary, created_at, updated_at, version
            """)
            .bind("id", id)
            .bind("rolloutId", rolloutId)
            .bind("stageIndex", stageIndex)
            .bind("percentage", percentage)
            .bind("minimumGatewayCount", minimumGatewayCount)
            .bind("requiredConvergedPercentage", requiredConvergedPercentage)
            .bind("maximumFailedGateways", maximumFailedGateways)
            .bind("maximumTimedOutGateways", maximumTimedOutGateways)
            .bind("requiredOnlinePercentage", requiredOnlinePercentage)
            .bind("observationDurationMs", observationDurationMs)
            .bind("stageTimeoutMs", stageTimeoutMs)
            .bind("status", status)
            .bind("createdAt", now)
            .bind("updatedAt", now);
    spec = bindNullableInteger(spec, "maximumGatewayCount", maximumGatewayCount);
    return spec.map(this::mapStage).one();
  }

  public Mono<Void> insertGatewayAssignment(
      UUID rolloutId,
      String gatewayId,
      long cohortRank,
      Integer assignedStageIndex,
      Long previousDesiredVersion,
      Long targetDesiredVersion,
      long assignmentGeneration,
      String status,
      boolean eligible,
      String exclusionReason,
      OffsetDateTime now) {
    DatabaseClient.GenericExecuteSpec spec =
        databaseClient
            .sql(
                """
            INSERT INTO runtime_rollout_gateways (
              rollout_id, gateway_id, cohort_rank, assigned_stage_index,
              previous_desired_version, target_desired_version, assignment_generation,
              rollback_generation, status, eligible, exclusion_reason, created_at, updated_at, version
            ) VALUES (
              :rolloutId, :gatewayId, :cohortRank, :assignedStageIndex,
              :previousDesiredVersion, :targetDesiredVersion, :assignmentGeneration,
              0, :status, :eligible, :exclusionReason, :createdAt, :updatedAt, 0
            )
            """)
            .bind("rolloutId", rolloutId)
            .bind("gatewayId", gatewayId)
            .bind("cohortRank", cohortRank)
            .bind("assignmentGeneration", assignmentGeneration)
            .bind("status", status)
            .bind("eligible", eligible)
            .bind("createdAt", now)
            .bind("updatedAt", now);
    spec = bindNullableInteger(spec, "assignedStageIndex", assignedStageIndex);
    spec = bindNullableLong(spec, "previousDesiredVersion", previousDesiredVersion);
    spec = bindNullableLong(spec, "targetDesiredVersion", targetDesiredVersion);
    spec = bindNullableString(spec, "exclusionReason", exclusionReason);
    return spec.fetch().rowsUpdated().then();
  }

  public Mono<RuntimeRolloutEntity> findById(UUID projectId, UUID rolloutId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, gateway_group_id, api_id, source_version, target_version,
                   strategy, progression_mode, status, current_stage_index, membership_mode,
                   auto_rollback_on_failure, cancel_behavior, correlation_id,
                   created_by_actor_type, created_by_actor_id, started_at, paused_at,
                   pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                   failure_summary, created_at, updated_at, version
            FROM runtime_rollouts
            WHERE id = :id AND project_id = :projectId
            """)
        .bind("id", rolloutId)
        .bind("projectId", projectId)
        .map(this::mapRollout)
        .one();
  }

  public Flux<RuntimeRolloutEntity> listByProject(
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
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, project_id, gateway_group_id, api_id, source_version, target_version,
                   strategy, progression_mode, status, current_stage_index, membership_mode,
                   auto_rollback_on_failure, cancel_behavior, correlation_id,
                   created_by_actor_type, created_by_actor_id, started_at, paused_at,
                   pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                   failure_summary, created_at, updated_at, version
            FROM runtime_rollouts
            WHERE project_id = :projectId
            """);
    if (gatewayGroupId != null) {
      sql.append(" AND gateway_group_id = :gatewayGroupId");
    }
    if (status != null) {
      sql.append(" AND status = :status");
    }
    if (strategy != null) {
      sql.append(" AND strategy = :strategy");
    }
    if (sourceVersion != null) {
      sql.append(" AND source_version = :sourceVersion");
    }
    if (targetVersion != null) {
      sql.append(" AND target_version = :targetVersion");
    }
    if (createdAfter != null) {
      sql.append(" AND created_at >= :createdAfter");
    }
    if (createdBefore != null) {
      sql.append(" AND created_at <= :createdBefore");
    }
    sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");

    DatabaseClient.GenericExecuteSpec spec =
        databaseClient.sql(sql.toString()).bind("projectId", projectId);
    spec = bindNullableUuid(spec, "gatewayGroupId", gatewayGroupId);
    spec = bindNullableString(spec, "status", status);
    spec = bindNullableString(spec, "strategy", strategy);
    spec = bindNullableLong(spec, "sourceVersion", sourceVersion);
    spec = bindNullableLong(spec, "targetVersion", targetVersion);
    spec = bindNullableTime(spec, "createdAfter", createdAfter);
    spec = bindNullableTime(spec, "createdBefore", createdBefore);
    return spec.bind("limit", limit).bind("offset", offset).map(this::mapRollout).all();
  }

  public Flux<RuntimeRolloutEntity> claimActiveRollouts(int batchSize, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE runtime_rollouts
            SET updated_at = :now
            WHERE id IN (
              SELECT id FROM runtime_rollouts
              WHERE status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK')
              ORDER BY updated_at
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            RETURNING id, project_id, gateway_group_id, api_id, source_version, target_version,
                      strategy, progression_mode, status, current_stage_index, membership_mode,
                      auto_rollback_on_failure, cancel_behavior, correlation_id,
                      created_by_actor_type, created_by_actor_id, started_at, paused_at,
                      pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                      failure_summary, created_at, updated_at, version
            """)
        .bind("now", now)
        .bind("batchSize", batchSize)
        .map(this::mapRollout)
        .all();
  }

  public Mono<RuntimeRolloutEntity> updateRolloutStatus(
      UUID rolloutId,
      String expectedStatus,
      String newStatus,
      long expectedVersion,
      OffsetDateTime now,
      String failureCode,
      String failureSummary) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE runtime_rollouts
                SET status = :newStatus,
                    updated_at = :updatedAt,
                    version = version + 1,
                    completed_at = CASE WHEN :newStatus IN ('SUCCEEDED', 'ROLLED_BACK') THEN :updatedAt ELSE completed_at END,
                    cancelled_at = CASE WHEN :newStatus = 'CANCELLED' THEN :updatedAt ELSE cancelled_at END,
                    failed_at = CASE WHEN :newStatus IN ('FAILED', 'ROLLBACK_FAILED') THEN :updatedAt ELSE failed_at END,
                    failure_code = COALESCE(:failureCode, failure_code),
                    failure_summary = COALESCE(:failureSummary, failure_summary)
                WHERE id = :id AND status = :expectedStatus AND version = :expectedVersion
                RETURNING id, project_id, gateway_group_id, api_id, source_version, target_version,
                          strategy, progression_mode, status, current_stage_index, membership_mode,
                          auto_rollback_on_failure, cancel_behavior, correlation_id,
                          created_by_actor_type, created_by_actor_id, started_at, paused_at,
                          pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                          failure_summary, created_at, updated_at, version
                """)
            .bind("id", rolloutId)
            .bind("expectedStatus", expectedStatus)
            .bind("newStatus", newStatus)
            .bind("expectedVersion", expectedVersion)
            .bind("updatedAt", now);
    spec = bindNullableString(spec, "failureCode", failureCode);
    spec = bindNullableString(spec, "failureSummary", failureSummary);
    return spec.map(this::mapRollout).one();
  }

  public Mono<RuntimeRolloutEntity> updateRolloutFields(
      UUID rolloutId,
      long expectedVersion,
      OffsetDateTime now,
      Integer currentStageIndex,
      OffsetDateTime startedAt,
      OffsetDateTime pausedAt,
      Long pauseAccumulatedMs,
      String status) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE runtime_rollouts
                SET updated_at = :updatedAt,
                    version = version + 1,
                    current_stage_index = COALESCE(:currentStageIndex, current_stage_index),
                    started_at = COALESCE(:startedAt, started_at),
                    paused_at = :pausedAt,
                    pause_accumulated_ms = COALESCE(:pauseAccumulatedMs, pause_accumulated_ms),
                    status = COALESCE(:status, status)
                WHERE id = :id AND version = :expectedVersion
                RETURNING id, project_id, gateway_group_id, api_id, source_version, target_version,
                          strategy, progression_mode, status, current_stage_index, membership_mode,
                          auto_rollback_on_failure, cancel_behavior, correlation_id,
                          created_by_actor_type, created_by_actor_id, started_at, paused_at,
                          pause_accumulated_ms, completed_at, cancelled_at, failed_at, failure_code,
                          failure_summary, created_at, updated_at, version
                """)
            .bind("id", rolloutId)
            .bind("expectedVersion", expectedVersion)
            .bind("updatedAt", now);
    spec = bindNullableInteger(spec, "currentStageIndex", currentStageIndex);
    spec = bindNullableTime(spec, "startedAt", startedAt);
    spec = bindNullableTime(spec, "pausedAt", pausedAt);
    spec = bindNullableLong(spec, "pauseAccumulatedMs", pauseAccumulatedMs);
    spec = bindNullableString(spec, "status", status);
    return spec.map(this::mapRollout).one();
  }

  public Flux<RuntimeRolloutStageEntity> listStagesByRollout(UUID rolloutId) {
    return databaseClient
        .sql(
            """
            SELECT id, rollout_id, stage_index, percentage, minimum_gateway_count,
                   maximum_gateway_count, required_converged_percentage,
                   maximum_failed_gateways, maximum_timed_out_gateways,
                   required_online_percentage, observation_duration_ms, stage_timeout_ms,
                   status, started_at, observation_started_at, completed_at, failed_at,
                   failure_code, failure_summary, created_at, updated_at, version
            FROM runtime_rollout_stages
            WHERE rollout_id = :rolloutId
            ORDER BY stage_index ASC
            """)
        .bind("rolloutId", rolloutId)
        .map(this::mapStage)
        .all();
  }

  public Mono<RuntimeRolloutStageEntity> findStage(UUID rolloutId, int stageIndex) {
    return databaseClient
        .sql(
            """
            SELECT id, rollout_id, stage_index, percentage, minimum_gateway_count,
                   maximum_gateway_count, required_converged_percentage,
                   maximum_failed_gateways, maximum_timed_out_gateways,
                   required_online_percentage, observation_duration_ms, stage_timeout_ms,
                   status, started_at, observation_started_at, completed_at, failed_at,
                   failure_code, failure_summary, created_at, updated_at, version
            FROM runtime_rollout_stages
            WHERE rollout_id = :rolloutId AND stage_index = :stageIndex
            """)
        .bind("rolloutId", rolloutId)
        .bind("stageIndex", stageIndex)
        .map(this::mapStage)
        .one();
  }

  public Mono<RuntimeRolloutStageEntity> updateStageStatus(
      UUID stageId,
      String expectedStatus,
      String newStatus,
      long expectedVersion,
      OffsetDateTime now,
      OffsetDateTime startedAt,
      OffsetDateTime observationStartedAt,
      OffsetDateTime completedAt,
      OffsetDateTime failedAt,
      String failureCode,
      String failureSummary) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE runtime_rollout_stages
                SET status = :newStatus,
                    updated_at = :updatedAt,
                    version = version + 1,
                    started_at = COALESCE(:startedAt, started_at),
                    observation_started_at = COALESCE(:observationStartedAt, observation_started_at),
                    completed_at = COALESCE(:completedAt, completed_at),
                    failed_at = COALESCE(:failedAt, failed_at),
                    failure_code = COALESCE(:failureCode, failure_code),
                    failure_summary = COALESCE(:failureSummary, failure_summary)
                WHERE id = :id AND status = :expectedStatus AND version = :expectedVersion
                RETURNING id, rollout_id, stage_index, percentage, minimum_gateway_count,
                          maximum_gateway_count, required_converged_percentage,
                          maximum_failed_gateways, maximum_timed_out_gateways,
                          required_online_percentage, observation_duration_ms, stage_timeout_ms,
                          status, started_at, observation_started_at, completed_at, failed_at,
                          failure_code, failure_summary, created_at, updated_at, version
                """)
            .bind("id", stageId)
            .bind("expectedStatus", expectedStatus)
            .bind("newStatus", newStatus)
            .bind("expectedVersion", expectedVersion)
            .bind("updatedAt", now);
    spec = bindNullableTime(spec, "startedAt", startedAt);
    spec = bindNullableTime(spec, "observationStartedAt", observationStartedAt);
    spec = bindNullableTime(spec, "completedAt", completedAt);
    spec = bindNullableTime(spec, "failedAt", failedAt);
    spec = bindNullableString(spec, "failureCode", failureCode);
    spec = bindNullableString(spec, "failureSummary", failureSummary);
    return spec.map(this::mapStage).one();
  }

  public Flux<RuntimeRolloutGatewayEntity> listGatewaysByRollout(
      UUID rolloutId, int limit, int offset) {
    return databaseClient
        .sql(
            """
            SELECT rollout_id, gateway_id, cohort_rank, assigned_stage_index,
                   previous_desired_version, target_desired_version, assignment_generation,
                   rollback_generation, status, eligible, exclusion_reason, assigned_at,
                   delivered_at, acknowledged_at, activated_at, failed_at, timed_out_at,
                   rolled_back_at, last_reported_version, last_error_code, last_error_summary,
                   created_at, updated_at, version
            FROM runtime_rollout_gateways
            WHERE rollout_id = :rolloutId
            ORDER BY cohort_rank ASC, gateway_id ASC
            LIMIT :limit OFFSET :offset
            """)
        .bind("rolloutId", rolloutId)
        .bind("limit", limit)
        .bind("offset", offset)
        .map(this::mapGateway)
        .all();
  }

  public Mono<RuntimeRolloutGatewayEntity> findGatewayAssignment(UUID rolloutId, String gatewayId) {
    return databaseClient
        .sql(
            """
            SELECT rollout_id, gateway_id, cohort_rank, assigned_stage_index,
                   previous_desired_version, target_desired_version, assignment_generation,
                   rollback_generation, status, eligible, exclusion_reason, assigned_at,
                   delivered_at, acknowledged_at, activated_at, failed_at, timed_out_at,
                   rolled_back_at, last_reported_version, last_error_code, last_error_summary,
                   created_at, updated_at, version
            FROM runtime_rollout_gateways
            WHERE rollout_id = :rolloutId AND gateway_id = :gatewayId
            """)
        .bind("rolloutId", rolloutId)
        .bind("gatewayId", gatewayId)
        .map(this::mapGateway)
        .one();
  }

  public Mono<RuntimeRolloutGatewayEntity> updateGatewayAssignment(
      UUID rolloutId,
      String gatewayId,
      String expectedStatus,
      String newStatus,
      long expectedVersion,
      OffsetDateTime now,
      Integer assignedStageIndex,
      Long targetDesiredVersion,
      Long assignmentGeneration,
      Long rollbackGeneration,
      OffsetDateTime assignedAt,
      OffsetDateTime acknowledgedAt,
      OffsetDateTime activatedAt,
      OffsetDateTime failedAt,
      OffsetDateTime timedOutAt,
      OffsetDateTime rolledBackAt,
      Long lastReportedVersion,
      String lastErrorCode,
      String lastErrorSummary) {
    var spec =
        databaseClient
            .sql(
                """
                UPDATE runtime_rollout_gateways
                SET status = :newStatus,
                    updated_at = :updatedAt,
                    version = version + 1,
                    assigned_stage_index = COALESCE(:assignedStageIndex, assigned_stage_index),
                    target_desired_version = COALESCE(:targetDesiredVersion, target_desired_version),
                    assignment_generation = COALESCE(:assignmentGeneration, assignment_generation),
                    rollback_generation = COALESCE(:rollbackGeneration, rollback_generation),
                    assigned_at = COALESCE(:assignedAt, assigned_at),
                    acknowledged_at = COALESCE(:acknowledgedAt, acknowledged_at),
                    activated_at = COALESCE(:activatedAt, activated_at),
                    failed_at = COALESCE(:failedAt, failed_at),
                    timed_out_at = COALESCE(:timedOutAt, timed_out_at),
                    rolled_back_at = COALESCE(:rolledBackAt, rolled_back_at),
                    last_reported_version = COALESCE(:lastReportedVersion, last_reported_version),
                    last_error_code = COALESCE(:lastErrorCode, last_error_code),
                    last_error_summary = COALESCE(:lastErrorSummary, last_error_summary)
                WHERE rollout_id = :rolloutId AND gateway_id = :gatewayId
                  AND status = :expectedStatus AND version = :expectedVersion
                RETURNING rollout_id, gateway_id, cohort_rank, assigned_stage_index,
                          previous_desired_version, target_desired_version, assignment_generation,
                          rollback_generation, status, eligible, exclusion_reason, assigned_at,
                          delivered_at, acknowledged_at, activated_at, failed_at, timed_out_at,
                          rolled_back_at, last_reported_version, last_error_code, last_error_summary,
                          created_at, updated_at, version
                """)
            .bind("rolloutId", rolloutId)
            .bind("gatewayId", gatewayId)
            .bind("expectedStatus", expectedStatus)
            .bind("newStatus", newStatus)
            .bind("expectedVersion", expectedVersion)
            .bind("updatedAt", now);
    spec = bindNullableInteger(spec, "assignedStageIndex", assignedStageIndex);
    spec = bindNullableLong(spec, "targetDesiredVersion", targetDesiredVersion);
    spec = bindNullableLong(spec, "assignmentGeneration", assignmentGeneration);
    spec = bindNullableLong(spec, "rollbackGeneration", rollbackGeneration);
    spec = bindNullableTime(spec, "assignedAt", assignedAt);
    spec = bindNullableTime(spec, "acknowledgedAt", acknowledgedAt);
    spec = bindNullableTime(spec, "activatedAt", activatedAt);
    spec = bindNullableTime(spec, "failedAt", failedAt);
    spec = bindNullableTime(spec, "timedOutAt", timedOutAt);
    spec = bindNullableTime(spec, "rolledBackAt", rolledBackAt);
    spec = bindNullableLong(spec, "lastReportedVersion", lastReportedVersion);
    spec = bindNullableString(spec, "lastErrorCode", lastErrorCode);
    spec = bindNullableString(spec, "lastErrorSummary", lastErrorSummary);
    return spec.map(this::mapGateway).one();
  }

  public Mono<Map<String, Long>> countAssignmentsByStatus(UUID rolloutId) {
    return databaseClient
        .sql(
            """
            SELECT status, COUNT(*) AS cnt
            FROM runtime_rollout_gateways
            WHERE rollout_id = :rolloutId
            GROUP BY status
            """)
        .bind("rolloutId", rolloutId)
        .map(row -> Map.entry(row.get("status", String.class), row.get("cnt", Long.class)))
        .all()
        .collectList()
        .map(
            entries -> {
              Map<String, Long> counts = new HashMap<>();
              for (Map.Entry<String, Long> entry : entries) {
                counts.put(entry.getKey(), entry.getValue());
              }
              return counts;
            });
  }

  public Mono<Long> countActiveRolloutsByProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT COUNT(*) AS cnt FROM runtime_rollouts
            WHERE project_id = :projectId
              AND status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK')
            """)
        .bind("projectId", projectId)
        .map(row -> row.get("cnt", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  public Mono<ActiveRolloutAssignmentRow> findActiveAssignmentForGateway(
      String gatewayId, UUID apiId) {
    return databaseClient
        .sql(
            """
            SELECT rg.rollout_id, rg.gateway_id, rg.cohort_rank, rg.assigned_stage_index,
                   rg.previous_desired_version, rg.target_desired_version,
                   rg.assignment_generation, rg.rollback_generation, rg.status,
                   rg.eligible, rg.exclusion_reason, rg.assigned_at, rg.delivered_at,
                   rg.acknowledged_at, rg.activated_at, rg.failed_at, rg.timed_out_at,
                   rg.rolled_back_at, rg.last_reported_version, rg.last_error_code,
                   rg.last_error_summary, rg.created_at, rg.updated_at, rg.version,
                   r.status AS rollout_status, r.source_version, r.target_version,
                   r.gateway_group_id, r.project_id
            FROM runtime_rollout_gateways rg
            JOIN runtime_rollouts r ON r.id = rg.rollout_id
            WHERE rg.gateway_id = :gatewayId
              AND r.api_id = :apiId
              AND r.status IN ('RUNNING', 'PAUSED', 'ROLLING_BACK')
            ORDER BY r.started_at DESC NULLS LAST, r.created_at DESC
            LIMIT 1
            """)
        .bind("gatewayId", gatewayId)
        .bind("apiId", apiId)
        .map(
            row ->
                new ActiveRolloutAssignmentRow(
                    mapGatewayRow(row),
                    row.get("rollout_status", String.class),
                    row.get("source_version", Long.class),
                    row.get("target_version", Long.class),
                    row.get("gateway_group_id", UUID.class),
                    row.get("project_id", UUID.class)))
        .one();
  }

  public Flux<GatewayPlacementRow> listGatewayPlacements() {
    return databaseClient
        .sql(
            """
            SELECT id, region, zone, environment, labels::text, admin_labels::text,
                   gateway_software_version, runtime_schema_version, capabilities::text,
                   last_seen_at
            FROM gateways
            ORDER BY id ASC
            """)
        .map(
            row ->
                new GatewayPlacementRow(
                    row.get("id", String.class),
                    row.get("region", String.class),
                    row.get("zone", String.class),
                    row.get("environment", String.class),
                    row.get("labels", String.class),
                    row.get("admin_labels", String.class),
                    row.get("gateway_software_version", String.class),
                    row.get("runtime_schema_version", Integer.class),
                    row.get("capabilities", String.class),
                    row.get("last_seen_at", OffsetDateTime.class)))
        .all();
  }

  private RuntimeRolloutEntity mapRollout(Row row, io.r2dbc.spi.RowMetadata metadata) {
    return new RuntimeRolloutEntity(
        row.get("id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("gateway_group_id", UUID.class),
        row.get("api_id", UUID.class),
        row.get("source_version", Long.class),
        row.get("target_version", Long.class),
        row.get("strategy", String.class),
        row.get("progression_mode", String.class),
        row.get("status", String.class),
        row.get("current_stage_index", Integer.class),
        row.get("membership_mode", String.class),
        Boolean.TRUE.equals(row.get("auto_rollback_on_failure", Boolean.class)),
        row.get("cancel_behavior", String.class),
        row.get("correlation_id", UUID.class),
        row.get("created_by_actor_type", String.class),
        row.get("created_by_actor_id", String.class),
        row.get("started_at", OffsetDateTime.class),
        row.get("paused_at", OffsetDateTime.class),
        row.get("pause_accumulated_ms", Long.class) == null
            ? 0L
            : row.get("pause_accumulated_ms", Long.class),
        row.get("completed_at", OffsetDateTime.class),
        row.get("cancelled_at", OffsetDateTime.class),
        row.get("failed_at", OffsetDateTime.class),
        row.get("failure_code", String.class),
        row.get("failure_summary", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("version", Long.class));
  }

  private RuntimeRolloutStageEntity mapStage(Row row, io.r2dbc.spi.RowMetadata metadata) {
    return new RuntimeRolloutStageEntity(
        row.get("id", UUID.class),
        row.get("rollout_id", UUID.class),
        row.get("stage_index", Integer.class),
        row.get("percentage", Integer.class),
        row.get("minimum_gateway_count", Integer.class),
        row.get("maximum_gateway_count", Integer.class),
        row.get("required_converged_percentage", Integer.class),
        row.get("maximum_failed_gateways", Integer.class),
        row.get("maximum_timed_out_gateways", Integer.class),
        row.get("required_online_percentage", Integer.class),
        row.get("observation_duration_ms", Long.class),
        row.get("stage_timeout_ms", Long.class),
        row.get("status", String.class),
        row.get("started_at", OffsetDateTime.class),
        row.get("observation_started_at", OffsetDateTime.class),
        row.get("completed_at", OffsetDateTime.class),
        row.get("failed_at", OffsetDateTime.class),
        row.get("failure_code", String.class),
        row.get("failure_summary", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("version", Long.class));
  }

  private RuntimeRolloutGatewayEntity mapGateway(Row row, io.r2dbc.spi.RowMetadata metadata) {
    return mapGatewayRow(row);
  }

  private RuntimeRolloutGatewayEntity mapGatewayRow(io.r2dbc.spi.Readable row) {
    return new RuntimeRolloutGatewayEntity(
        row.get("rollout_id", UUID.class),
        row.get("gateway_id", String.class),
        row.get("cohort_rank", Long.class),
        row.get("assigned_stage_index", Integer.class),
        row.get("previous_desired_version", Long.class),
        row.get("target_desired_version", Long.class),
        row.get("assignment_generation", Long.class),
        row.get("rollback_generation", Long.class),
        row.get("status", String.class),
        Boolean.TRUE.equals(row.get("eligible", Boolean.class)),
        row.get("exclusion_reason", String.class),
        row.get("assigned_at", OffsetDateTime.class),
        row.get("delivered_at", OffsetDateTime.class),
        row.get("acknowledged_at", OffsetDateTime.class),
        row.get("activated_at", OffsetDateTime.class),
        row.get("failed_at", OffsetDateTime.class),
        row.get("timed_out_at", OffsetDateTime.class),
        row.get("rolled_back_at", OffsetDateTime.class),
        row.get("last_reported_version", Long.class),
        row.get("last_error_code", String.class),
        row.get("last_error_summary", String.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("version", Long.class));
  }

  private DatabaseClient.GenericExecuteSpec bindNullableUuid(
      DatabaseClient.GenericExecuteSpec spec, String name, UUID value) {
    return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableLong(
      DatabaseClient.GenericExecuteSpec spec, String name, Long value) {
    return value == null ? spec.bindNull(name, Long.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableInteger(
      DatabaseClient.GenericExecuteSpec spec, String name, Integer value) {
    return value == null ? spec.bindNull(name, Integer.class) : spec.bind(name, value);
  }

  private DatabaseClient.GenericExecuteSpec bindNullableTime(
      DatabaseClient.GenericExecuteSpec spec, String name, OffsetDateTime value) {
    return value == null ? spec.bindNull(name, OffsetDateTime.class) : spec.bind(name, value);
  }

  public record ActiveRolloutAssignmentRow(
      RuntimeRolloutGatewayEntity assignment,
      String rolloutStatus,
      long sourceVersion,
      long targetVersion,
      UUID gatewayGroupId,
      UUID projectId) {}

  public record GatewayPlacementRow(
      String gatewayId,
      String region,
      String zone,
      String environment,
      String labelsJson,
      String adminLabelsJson,
      String gatewaySoftwareVersion,
      Integer runtimeSchemaVersion,
      String capabilitiesJson,
      OffsetDateTime lastSeenAt) {}
}
