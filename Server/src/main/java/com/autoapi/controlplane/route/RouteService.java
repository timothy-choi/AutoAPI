package com.autoapi.controlplane.route;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.upstream.UpstreamService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RouteService {

  private static final List<String> SUPPORTED =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private final RouteRepository routeRepository;
  private final ApiDefinitionService apiDefinitionService;
  private final UpstreamService upstreamService;

  public RouteService(
      RouteRepository routeRepository,
      ApiDefinitionService apiDefinitionService,
      UpstreamService upstreamService) {
    this.routeRepository = routeRepository;
    this.apiDefinitionService = apiDefinitionService;
    this.upstreamService = upstreamService;
  }

  public Mono<RouteEntity> create(
      UUID apiId,
      String name,
      String host,
      String pathPrefix,
      List<String> methods,
      UUID upstreamPoolId,
      boolean enabled) {
    if (pathPrefix == null || pathPrefix.isBlank() || !pathPrefix.startsWith("/")) {
      return Mono.error(
          ControlPlaneException.invalidRequest("Route pathPrefix must begin with '/'"));
    }
    if (methods == null || methods.isEmpty()) {
      return Mono.error(ControlPlaneException.invalidRequest("Route methods must not be empty"));
    }
    for (String method : methods) {
      if (method == null || !SUPPORTED.contains(method.toUpperCase(Locale.ROOT))) {
        return Mono.error(
            ControlPlaneException.invalidRequest("Unsupported HTTP method: " + method));
      }
    }
    String[] normalized =
        methods.stream()
            .map(m -> m.toUpperCase(Locale.ROOT))
            .distinct()
            .sorted()
            .toArray(String[]::new);

    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api ->
                upstreamService
                    .getPool(upstreamPoolId)
                    .flatMap(
                        pool -> {
                          if (!pool.apiId().equals(api.id())) {
                            return Mono.error(
                                ControlPlaneException.invalidRequest(
                                    "Route references upstream pool from another API"));
                          }
                          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                          RouteEntity entity =
                              new RouteEntity(
                                  UUID.randomUUID(),
                                  api.id(),
                                  name,
                                  host,
                                  pathPrefix,
                                  normalized,
                                  upstreamPoolId,
                                  null,
                                  enabled,
                                  now,
                                  now);
                          return routeRepository
                              .save(entity)
                              .onErrorMap(
                                  DataIntegrityViolationException.class,
                                  ex ->
                                      ControlPlaneException.conflict(
                                          "Route name already exists for API"));
                        }));
  }

  public Flux<RouteEntity> listByApi(UUID apiId) {
    return apiDefinitionService.get(apiId).thenMany(routeRepository.findByApiId(apiId));
  }
}
