package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RuntimeRolloutEntity(
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
    String membershipMode,
    boolean autoRollbackOnFailure,
    String cancelBehavior,
    UUID correlationId,
    String createdByActorType,
    String createdByActorId,
    OffsetDateTime startedAt,
    OffsetDateTime pausedAt,
    long pauseAccumulatedMs,
    OffsetDateTime completedAt,
    OffsetDateTime cancelledAt,
    OffsetDateTime failedAt,
    String failureCode,
    String failureSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long version) {}
