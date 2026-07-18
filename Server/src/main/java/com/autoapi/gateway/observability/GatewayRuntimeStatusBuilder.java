package com.autoapi.gateway.observability;

import com.autoapi.config.RuntimeSnapshotMetadata;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.gateway.traffic.TrafficSplitReconciler;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayRuntimeStatusBuilder {

  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final GatewayProperties gatewayProperties;
  private final GatewayInstanceIdentity identity;
  private final GatewayRequestSummaryBuffer summaryBuffer;
  private final ObjectMapper objectMapper;
  private final Instant startedAt = Instant.now();

  public GatewayRuntimeStatusBuilder(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      GatewayProperties gatewayProperties,
      GatewayInstanceIdentity identity,
      GatewayRequestSummaryBuffer summaryBuffer,
      ObjectMapper objectMapper) {
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.gatewayProperties = gatewayProperties;
    this.identity = identity;
    this.summaryBuffer = summaryBuffer;
    this.objectMapper = objectMapper;
  }

  public HeartbeatPayload build() {
    ActiveRuntimeBundle active = activeRuntimeConfigHolder.getActive();
    Long activeVersion = active == null ? null : active.version();
    OffsetDateTime activatedAt =
        active == null ? null : OffsetDateTime.ofInstant(active.activatedAt(), ZoneOffset.UTC);
    RuntimeSnapshotMetadata metadata = active == null ? null : active.metadata();
    int routeCount = metadata == null ? 0 : metadata.routeCount();
    int targetCount = metadata == null ? 0 : metadata.targetCount();
    if (active != null && routeCount == 0) {
      routeCount = active.runtimeConfig().routes().size();
      targetCount = TrafficSplitReconciler.countTargets(active);
    }
    long uptimeSeconds = Duration.between(startedAt, Instant.now()).getSeconds();
    String status = activeRuntimeConfigHolder.hasActiveConfig() ? "READY" : "STARTING";
    List<Map<String, Object>> summaries =
        summaryBuffer.drainForExport(10).stream()
            .map(
                summary ->
                    Map.<String, Object>of(
                        "requestId", summary.requestId(),
                        "traceId", summary.traceId() == null ? "" : summary.traceId(),
                        "apiId", summary.apiId() == null ? "" : summary.apiId(),
                        "routeId", summary.routeId() == null ? "" : summary.routeId(),
                        "method", summary.method(),
                        "status", summary.status(),
                        "durationMs", summary.durationMs(),
                        "attemptCount", summary.attemptCount(),
                        "retryCount", summary.retryCount(),
                        "fallbackUsed", summary.fallbackUsed()))
            .toList();
    return new HeartbeatPayload(
        identity.gatewayId(),
        identity.instanceId(),
        status,
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        gatewayProperties.softwareVersion() == null
            ? "0.1.0-SNAPSHOT"
            : gatewayProperties.softwareVersion(),
        activeVersion,
        activatedAt,
        routeCount,
        targetCount,
        uptimeSeconds,
        active == null
            ? List.of()
            : List.of(new ApiStatus(active.apiId(), active.version(), active.contentHash())),
        summaries,
        safeMetadataJson(Map.of("configSource", gatewayProperties.configSource().name())));
  }

  private String safeMetadataJson(Map<String, Object> metadata) {
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  public record ApiStatus(java.util.UUID apiId, long activeVersion, String activeContentHash) {}

  public record HeartbeatPayload(
      String gatewayId,
      String instanceId,
      String status,
      OffsetDateTime startedAt,
      String softwareVersion,
      Long activeSnapshotVersion,
      OffsetDateTime activeSnapshotActivatedAt,
      int routeCount,
      int targetCount,
      long uptimeSeconds,
      List<ApiStatus> apiStatuses,
      List<Map<String, Object>> requestSummaries,
      String metadataJson) {}
}
