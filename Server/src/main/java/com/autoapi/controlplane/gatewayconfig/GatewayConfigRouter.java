package com.autoapi.controlplane.gatewayconfig;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Configuration
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayConfigRouter {

  @Bean
  @Order(4)
  RouterFunction<ServerResponse> gatewayConfigRoutes(GatewayConfigService gatewayConfigService) {
    Handler handler = new Handler(gatewayConfigService);
    return RouterFunctions.route()
        .path(
            "/api/v1/gateway-config",
            builder ->
                builder
                    .GET("/{apiId}/desired", handler::getDesired)
                    .GET("/{apiId}/versions/{version}", handler::getSnapshot))
        .build();
  }

  static final class Handler {

    private final GatewayConfigService gatewayConfigService;

    Handler(GatewayConfigService gatewayConfigService) {
      this.gatewayConfigService = gatewayConfigService;
    }

    Mono<ServerResponse> getDesired(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      return gatewayConfigService
          .getDesiredMetadata(apiId)
          .flatMap(
              metadata -> {
                String etag = etag(metadata.contentHash());
                String ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH);
                if (matchesEtag(ifNoneMatch, metadata.contentHash())) {
                  return ServerResponse.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
                }
                return ServerResponse.ok()
                    .eTag(etag)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                        new DesiredResponse(
                            metadata.apiId(),
                            metadata.version(),
                            metadata.contentHash(),
                            metadata.snapshotUrl()));
              })
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getSnapshot(ServerRequest request) {
      UUID apiId = uuid(request, "apiId");
      long version = Long.parseLong(request.pathVariable("version"));
      return gatewayConfigService
          .getSnapshotEntity(apiId, version)
          .flatMap(
              entity ->
                  gatewayConfigService
                      .readSnapshot(entity)
                      .flatMap(snapshot -> snapshotResponse(snapshot, request)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<ServerResponse> snapshotResponse(
        StoredRuntimeSnapshot snapshot, ServerRequest request) {
      String etag = etag(snapshot.contentHash());
      String ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH);
      if (matchesEtag(ifNoneMatch, snapshot.contentHash())) {
        return ServerResponse.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
      }
      return ServerResponse.ok()
          .eTag(etag)
          .header(HttpHeaders.CACHE_CONTROL, "private, immutable")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(snapshot);
    }

    private Mono<ServerResponse> error(ControlPlaneException ex) {
      return ServerResponse.status(ex.status())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              java.util.Map.of(
                  "error",
                  java.util.Map.of(
                      "code", ex.code(),
                      "message", ex.getMessage(),
                      "details", ex.validationErrors())));
    }

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }

    private static String etag(String contentHash) {
      return "\"" + contentHash + "\"";
    }

    private static boolean matchesEtag(String headerValue, String contentHash) {
      if (headerValue == null || headerValue.isBlank()) {
        return false;
      }
      String trimmed = headerValue.trim();
      if ("*".equals(trimmed)) {
        return true;
      }
      for (String token : trimmed.split(",")) {
        String candidate = token.trim();
        if (candidate.startsWith("W/")) {
          candidate = candidate.substring(2).trim();
        }
        if (candidate.equals(contentHash) || candidate.equals("\"" + contentHash + "\"")) {
          return true;
        }
      }
      return false;
    }

    record DesiredResponse(UUID apiId, long version, String contentHash, String snapshotUrl) {}
  }
}
