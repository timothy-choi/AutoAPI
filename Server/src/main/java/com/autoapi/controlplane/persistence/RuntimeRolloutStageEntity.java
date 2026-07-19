package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RuntimeRolloutStageEntity(
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
    OffsetDateTime startedAt,
    OffsetDateTime observationStartedAt,
    OffsetDateTime completedAt,
    OffsetDateTime failedAt,
    String failureCode,
    String failureSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long version) {}
