package com.autoapi.controlplane.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RuntimeRolloutGatewayEntity(
    UUID rolloutId,
    String gatewayId,
    long cohortRank,
    Integer assignedStageIndex,
    Long previousDesiredVersion,
    Long targetDesiredVersion,
    long assignmentGeneration,
    long rollbackGeneration,
    String status,
    boolean eligible,
    String exclusionReason,
    OffsetDateTime assignedAt,
    OffsetDateTime deliveredAt,
    OffsetDateTime acknowledgedAt,
    OffsetDateTime activatedAt,
    OffsetDateTime failedAt,
    OffsetDateTime timedOutAt,
    OffsetDateTime rolledBackAt,
    Long lastReportedVersion,
    String lastErrorCode,
    String lastErrorSummary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long version) {}
