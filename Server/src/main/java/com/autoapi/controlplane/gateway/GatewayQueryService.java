package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.persistence.ConfigActivationEventEntity;
import com.autoapi.controlplane.persistence.ConfigActivationEventRepository;
import com.autoapi.controlplane.persistence.GatewayApiStatusEntity;
import com.autoapi.controlplane.persistence.GatewayApiStatusRepository;
import com.autoapi.controlplane.persistence.GatewayEntity;
import com.autoapi.controlplane.persistence.GatewayRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayQueryService {

  private static final int MAX_ACTIVATION_EVENTS = 100;

  private final GatewayRepository gatewayRepository;
  private final GatewayApiStatusRepository gatewayApiStatusRepository;
  private final ConfigActivationEventRepository activationEventRepository;
  private final ControlPlaneProperties controlPlaneProperties;

  public GatewayQueryService(
      GatewayRepository gatewayRepository,
      GatewayApiStatusRepository gatewayApiStatusRepository,
      ConfigActivationEventRepository activationEventRepository,
      ControlPlaneProperties controlPlaneProperties) {
    this.gatewayRepository = gatewayRepository;
    this.gatewayApiStatusRepository = gatewayApiStatusRepository;
    this.activationEventRepository = activationEventRepository;
    this.controlPlaneProperties = controlPlaneProperties;
  }

  public Flux<GatewaySummary> listGateways(String gatewayGroup, String livenessFilter) {
    OffsetDateTime staleCutoff =
        OffsetDateTime.now(ZoneOffset.UTC).minus(controlPlaneProperties.gatewayStaleAfter());
    Flux<GatewayEntity> source =
        gatewayGroup == null || gatewayGroup.isBlank()
            ? gatewayRepository.findAll()
            : gatewayRepository.findByGatewayGroup(gatewayGroup);
    return source
        .map(gateway -> GatewaySummary.from(gateway, staleCutoff))
        .filter(
            summary ->
                livenessFilter == null
                    || livenessFilter.isBlank()
                    || summary.liveness().equalsIgnoreCase(livenessFilter));
  }

  public Mono<GatewayDetail> getGateway(String gatewayId) {
    OffsetDateTime staleCutoff =
        OffsetDateTime.now(ZoneOffset.UTC).minus(controlPlaneProperties.gatewayStaleAfter());
    return gatewayRepository
        .findById(gatewayId)
        .switchIfEmpty(
            Mono.error(
                com.autoapi.controlplane.api.ControlPlaneException.notFound(
                    "Gateway was not found")))
        .flatMap(
            gateway ->
                gatewayApiStatusRepository
                    .findByGatewayId(gatewayId)
                    .map(ApiStatusView::from)
                    .collectList()
                    .map(
                        statuses ->
                            new GatewayDetail(
                                GatewaySummary.from(gateway, staleCutoff), statuses)));
  }

  public Flux<ActivationEventView> listActivationEvents(UUID apiId, Integer limit) {
    int effectiveLimit = limit == null ? 50 : Math.min(Math.max(limit, 1), MAX_ACTIVATION_EVENTS);
    return activationEventRepository
        .findByApiIdOrderByCreatedAtDesc(apiId)
        .take(effectiveLimit)
        .map(ActivationEventView::from);
  }

  public record GatewaySummary(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      String startedAt,
      String lastSeenAt,
      String liveness) {

    static GatewaySummary from(GatewayEntity gateway, OffsetDateTime staleCutoff) {
      boolean live = gateway.lastSeenAt() != null && !gateway.lastSeenAt().isBefore(staleCutoff);
      return new GatewaySummary(
          gateway.id(),
          gateway.gatewayGroup(),
          gateway.softwareVersion(),
          gateway.startedAt().toString(),
          gateway.lastSeenAt().toString(),
          live ? "LIVE" : "STALE");
    }
  }

  public record ApiStatusView(
      UUID apiId,
      Long activeVersion,
      String activeContentHash,
      Long lastAttemptedVersion,
      String lastStatus,
      String lastErrorCode,
      String lastReportedAt) {

    static ApiStatusView from(GatewayApiStatusEntity entity) {
      return new ApiStatusView(
          entity.apiId(),
          entity.activeVersion(),
          entity.activeContentHash(),
          entity.lastAttemptedVersion(),
          entity.lastStatus(),
          entity.lastErrorCode(),
          entity.lastReportedAt().toString());
    }
  }

  public record GatewayDetail(GatewaySummary gateway, List<ApiStatusView> apiStatuses) {}

  public record ActivationEventView(
      UUID id,
      String gatewayId,
      UUID apiId,
      long version,
      String contentHash,
      UUID reportId,
      String status,
      String errorCode,
      Long applyDurationMs,
      String createdAt) {

    static ActivationEventView from(ConfigActivationEventEntity entity) {
      return new ActivationEventView(
          entity.id(),
          entity.gatewayId(),
          entity.apiId(),
          entity.version(),
          entity.contentHash(),
          entity.reportId(),
          entity.status(),
          entity.errorCode(),
          entity.applyDurationMs(),
          entity.createdAt().toString());
    }
  }
}
