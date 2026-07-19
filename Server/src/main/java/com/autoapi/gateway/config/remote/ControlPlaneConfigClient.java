package com.autoapi.gateway.config.remote;

import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.gateway.ControlPlaneClientProperties;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class ControlPlaneConfigClient {

  private final WebClient webClient;
  private final GatewayProperties gatewayProperties;
  private final ObjectMapper objectMapper;

  public ControlPlaneConfigClient(
      GatewayProperties gatewayProperties,
      ObjectMapper objectMapper,
      WebClient.Builder webClientBuilder) {
    this.gatewayProperties = gatewayProperties;
    this.objectMapper = objectMapper;
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

  public Mono<Optional<DesiredMetadataResponse>> fetchDesiredMetadata(String ifNoneMatch) {
    UUID apiId = gatewayProperties.apiId();
    String gatewayId = gatewayProperties.gatewayId();
    String uri =
        gatewayProperties.controlPlaneBaseUrl()
            + "/api/v1/gateway-config/"
            + apiId
            + "/desired?gatewayId="
            + gatewayId;
    return webClient
        .get()
        .uri(uri)
        .headers(
            headers -> {
              if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
                headers.set(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
              }
            })
        .exchangeToMono(
            response -> {
              if (response.statusCode().equals(HttpStatusCode.valueOf(304))) {
                return Mono.just(Optional.empty());
              }
              if (response.statusCode().isError()) {
                return response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(
                        body ->
                            Mono.error(
                                new ControlPlaneConfigClientException(
                                    "Desired metadata request failed with status "
                                        + response.statusCode().value())));
              }
              return response
                  .bodyToMono(String.class)
                  .flatMap(
                      body -> {
                        try {
                          DesiredMetadataResponse parsed =
                              objectMapper.readValue(body, DesiredMetadataResponse.class);
                          return Mono.just(Optional.of(parsed));
                        } catch (Exception e) {
                          return Mono.error(
                              new ControlPlaneConfigClientException(
                                  "Failed to parse desired metadata response"));
                        }
                      });
            });
  }

  public Mono<StoredRuntimeSnapshot> fetchSnapshot(long version, String expectedContentHash) {
    UUID apiId = gatewayProperties.apiId();
    return webClient
        .get()
        .uri(
            gatewayProperties.controlPlaneBaseUrl()
                + "/api/v1/gateway-config/{apiId}/versions/{version}",
            apiId,
            version)
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(
            body -> {
              try {
                StoredRuntimeSnapshot snapshot =
                    objectMapper.readValue(body, StoredRuntimeSnapshot.class);
                if (expectedContentHash != null
                    && !expectedContentHash.equals(snapshot.contentHash())) {
                  return Mono.error(
                      new ControlPlaneConfigClientException("Snapshot content hash mismatch"));
                }
                return Mono.just(snapshot);
              } catch (Exception e) {
                return Mono.error(
                    new ControlPlaneConfigClientException("Failed to parse snapshot response"));
              }
            });
  }

  public record DesiredMetadataResponse(
      UUID apiId,
      long version,
      String contentHash,
      String snapshotUrl,
      UUID rolloutId,
      Integer rolloutStageIndex,
      Long assignmentGeneration,
      String desiredSource) {

    public String etagToken() {
      if (rolloutId != null && assignmentGeneration != null) {
        return contentHash + ":" + rolloutId + ":" + assignmentGeneration;
      }
      return contentHash;
    }
  }
}
