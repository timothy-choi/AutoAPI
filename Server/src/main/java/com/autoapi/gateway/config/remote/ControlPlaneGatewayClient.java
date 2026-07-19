package com.autoapi.gateway.config.remote;

import com.autoapi.gateway.ControlPlaneClientProperties;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.GatewayActivationAttempt;
import com.autoapi.gateway.config.remote.ControlPlaneGatewayClient.ConfigStatusPayload;
import com.autoapi.gateway.config.remote.ControlPlaneGatewayClient.RegistrationPayload;
import com.autoapi.gateway.observability.GatewayRuntimeStatusBuilder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import io.netty.channel.ChannelOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class ControlPlaneGatewayClient {

  private final WebClient webClient;
  private final GatewayProperties gatewayProperties;
  private final String softwareVersion;

  public ControlPlaneGatewayClient(
      GatewayProperties gatewayProperties,
      WebClient.Builder webClientBuilder,
      @Value("${autoapi.gateway.software-version:0.1.0-SNAPSHOT}") String softwareVersion) {
    this.gatewayProperties = gatewayProperties;
    this.softwareVersion = softwareVersion;
    ControlPlaneClientProperties clientProperties = gatewayProperties.controlPlaneClient();
    HttpClient httpClient =
        HttpClient.create()
            .responseTimeout(clientProperties.responseTimeout())
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) clientProperties.connectTimeout().toMillis());
    this.webClient =
        webClientBuilder
            .clientConnector(
                new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
            .codecs(
                configurer ->
                    configurer
                        .defaultCodecs()
                        .maxInMemorySize(clientProperties.maxInMemorySizeBytes()))
            .build();
  }

  public Mono<Void> register() {
    RegistrationPayload payload =
        new RegistrationPayload(
            gatewayProperties.gatewayId(),
            gatewayProperties.gatewayGroup(),
            softwareVersion,
            OffsetDateTime.now(ZoneOffset.UTC),
            Map.of("configSource", gatewayProperties.configSource().name()));
    return webClient
        .post()
        .uri(gatewayProperties.controlPlaneBaseUrl() + "/api/v1/gateways/register")
        .bodyValue(payload)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(
                        body ->
                            Mono.error(
                                new ControlPlaneConfigClientException(
                                    "Gateway registration failed with status "
                                        + response.statusCode().value()))))
        .bodyToMono(Void.class)
        .then();
  }

  public Mono<Void> heartbeat(GatewayRuntimeStatusBuilder.HeartbeatPayload payload) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("sentAt", OffsetDateTime.now(ZoneOffset.UTC));
    if (payload.apiStatuses() != null && !payload.apiStatuses().isEmpty()) {
      body.put(
          "apiStatuses",
          payload.apiStatuses().stream()
              .map(
                  status ->
                      Map.of(
                          "apiId", status.apiId(),
                          "activeVersion", status.activeVersion(),
                          "activeContentHash", status.activeContentHash()))
              .toList());
    }
    body.put("instanceId", payload.instanceId());
    body.put("status", payload.status());
    body.put("startedAt", payload.startedAt());
    body.put("softwareVersion", payload.softwareVersion());
    body.put("activeSnapshotVersion", payload.activeSnapshotVersion());
    body.put("activeSnapshotActivatedAt", payload.activeSnapshotActivatedAt());
    body.put("routeCount", payload.routeCount());
    body.put("targetCount", payload.targetCount());
    body.put("uptimeSeconds", payload.uptimeSeconds());
    body.put("metadata", Map.of("configSource", gatewayProperties.configSource().name()));
    if (payload.requestSummaries() != null && !payload.requestSummaries().isEmpty()) {
      body.put("requestSummaries", payload.requestSummaries());
    }
    return postHeartbeat(body);
  }

  public Mono<Void> heartbeat(UUID apiId, Long activeVersion, String activeContentHash) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("sentAt", OffsetDateTime.now(ZoneOffset.UTC));
    if (apiId != null && activeVersion != null && activeContentHash != null) {
      body.put(
          "apiStatuses",
          java.util.List.of(
              Map.of(
                  "apiId", apiId,
                  "activeVersion", activeVersion,
                  "activeContentHash", activeContentHash)));
    }
    return postHeartbeat(body);
  }

  private Mono<Void> postHeartbeat(Map<String, Object> body) {
    return webClient
        .post()
        .uri(
            gatewayProperties.controlPlaneBaseUrl() + "/api/v1/gateways/{gatewayId}/heartbeat",
            gatewayProperties.gatewayId())
        .bodyValue(body)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            response ->
                Mono.error(
                    new ControlPlaneConfigClientException(
                        "Gateway heartbeat failed with status " + response.statusCode().value())))
        .bodyToMono(Void.class)
        .then();
  }

  public Mono<Void> reportConfigStatus(ConfigStatusPayload payload) {
    return webClient
        .post()
        .uri(
            gatewayProperties.controlPlaneBaseUrl() + "/api/v1/gateways/{gatewayId}/config-status",
            gatewayProperties.gatewayId())
        .bodyValue(payload)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            response ->
                Mono.error(
                    new ControlPlaneConfigClientException(
                        "Config status report failed with status "
                            + response.statusCode().value())))
        .bodyToMono(Void.class)
        .then();
  }

  public record RegistrationPayload(
      String gatewayId,
      String gatewayGroup,
      String softwareVersion,
      OffsetDateTime startedAt,
      Map<String, Object> metadata) {}

  public record ConfigStatusPayload(
      UUID reportId,
      UUID apiId,
      long version,
      String contentHash,
      String status,
      String errorCode,
      String diagnostic,
      Long applyDurationMs,
      UUID rolloutId,
      Long assignmentGeneration) {

    public static ConfigStatusPayload ack(
        UUID reportId,
        UUID apiId,
        long version,
        String contentHash,
        long applyDurationMs,
        UUID rolloutId,
        Long assignmentGeneration) {
      return new ConfigStatusPayload(
          reportId,
          apiId,
          version,
          contentHash,
          "ACK",
          null,
          null,
          applyDurationMs,
          rolloutId,
          assignmentGeneration);
    }

    public static ConfigStatusPayload nack(
        UUID reportId,
        UUID apiId,
        long version,
        String contentHash,
        String errorCode,
        String diagnostic,
        long applyDurationMs,
        UUID rolloutId,
        Long assignmentGeneration) {
      return new ConfigStatusPayload(
          reportId,
          apiId,
          version,
          contentHash,
          "NACK",
          errorCode,
          diagnostic,
          applyDurationMs,
          rolloutId,
          assignmentGeneration);
    }

    public static ConfigStatusPayload fromAttempt(
        UUID reportId,
        UUID apiId,
        GatewayActivationAttempt attempt,
        UUID rolloutId,
        Long assignmentGeneration) {
      if (attempt.success()) {
        return ack(
            reportId,
            apiId,
            attempt.version(),
            attempt.contentHash(),
            attempt.applyDurationMs(),
            rolloutId,
            assignmentGeneration);
      }
      return nack(
          reportId,
          apiId,
          attempt.version(),
          attempt.contentHash(),
          attempt.errorCode(),
          attempt.diagnostic(),
          attempt.applyDurationMs(),
          rolloutId,
          assignmentGeneration);
    }
  }
}
