package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingRepository;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.persistence.RouteRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DiscoveredServiceRouteBindingService {

  private final RouteRepository routeRepository;
  private final RouteRepositoryCustom routeRepositoryCustom;
  private final RoutePolicyBindingRepository routePolicyBindingRepository;
  private final ApiDefinitionService apiDefinitionService;
  private final DiscoveredServiceService discoveredServiceService;
  private final DatabaseClient databaseClient;

  public DiscoveredServiceRouteBindingService(
      RouteRepository routeRepository,
      RouteRepositoryCustom routeRepositoryCustom,
      RoutePolicyBindingRepository routePolicyBindingRepository,
      ApiDefinitionService apiDefinitionService,
      DiscoveredServiceService discoveredServiceService,
      DatabaseClient databaseClient) {
    this.routeRepository = routeRepository;
    this.routeRepositoryCustom = routeRepositoryCustom;
    this.routePolicyBindingRepository = routePolicyBindingRepository;
    this.apiDefinitionService = apiDefinitionService;
    this.discoveredServiceService = discoveredServiceService;
    this.databaseClient = databaseClient;
  }

  public Mono<RouteEntity> bindDiscoveredService(UUID routeId, UUID discoveredServiceId) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                apiDefinitionService
                    .get(route.apiId())
                    .flatMap(
                        api ->
                            routePolicyBindingRepository
                                .findById(routeId)
                                .flatMap(
                                    binding -> {
                                      if (binding != null
                                          && binding.trafficSplitPolicyId() != null) {
                                        return Mono.error(
                                            ControlPlaneException.conflict(
                                                "Route already bound to a traffic split policy"));
                                      }
                                      return discoveredServiceService
                                          .get(api.projectId(), discoveredServiceId)
                                          .then(updateRouteBinding(route, discoveredServiceId));
                                    })
                                .switchIfEmpty(
                                    discoveredServiceService
                                        .get(api.projectId(), discoveredServiceId)
                                        .then(updateRouteBinding(route, discoveredServiceId)))));
  }

  public Mono<RouteEntity> unbindDiscoveredService(UUID routeId) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route -> {
              if (route.discoveredServiceId() == null) {
                return Mono.just(route);
              }
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              return databaseClient
                  .sql(
                      """
                      UPDATE routes
                      SET discovered_service_id = NULL, updated_at = :updatedAt
                      WHERE id = :routeId
                      RETURNING id, api_id, name, host, path_prefix, methods, upstream_pool_id,
                                discovered_service_id, enabled, created_at, updated_at
                      """)
                  .bind("routeId", routeId)
                  .bind("updatedAt", now)
                  .map(
                      (row, metadata) ->
                          new RouteEntity(
                              row.get("id", UUID.class),
                              row.get("api_id", UUID.class),
                              row.get("name", String.class),
                              row.get("host", String.class),
                              row.get("path_prefix", String.class),
                              row.get("methods", String[].class),
                              row.get("upstream_pool_id", UUID.class),
                              row.get("discovered_service_id", UUID.class),
                              Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                              row.get("created_at", OffsetDateTime.class),
                              row.get("updated_at", OffsetDateTime.class)))
                  .one();
            });
  }

  private Mono<RouteEntity> updateRouteBinding(RouteEntity route, UUID discoveredServiceId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return databaseClient
        .sql(
            """
            UPDATE routes
            SET discovered_service_id = :discoveredServiceId,
                upstream_pool_id = NULL,
                updated_at = :updatedAt
            WHERE id = :routeId
            RETURNING id, api_id, name, host, path_prefix, methods, upstream_pool_id,
                      discovered_service_id, enabled, created_at, updated_at
            """)
        .bind("routeId", route.id())
        .bind("discoveredServiceId", discoveredServiceId)
        .bind("updatedAt", now)
        .map(
            (row, metadata) ->
                new RouteEntity(
                    row.get("id", UUID.class),
                    row.get("api_id", UUID.class),
                    row.get("name", String.class),
                    row.get("host", String.class),
                    row.get("path_prefix", String.class),
                    row.get("methods", String[].class),
                    row.get("upstream_pool_id", UUID.class),
                    row.get("discovered_service_id", UUID.class),
                    Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                    row.get("created_at", OffsetDateTime.class),
                    row.get("updated_at", OffsetDateTime.class)))
        .one();
  }
}
