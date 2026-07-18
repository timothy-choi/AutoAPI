package com.autoapi.controlplane.api;

import com.autoapi.controlplane.discovery.CreatedRegistrationCredentialResponse;
import com.autoapi.controlplane.discovery.DiscoveredServiceResponse;
import com.autoapi.controlplane.discovery.DiscoveredServiceRouteBindingService;
import com.autoapi.controlplane.discovery.DiscoveredServiceService;
import com.autoapi.controlplane.discovery.ServiceInstanceResponse;
import com.autoapi.controlplane.discovery.ServiceInstanceService;
import com.autoapi.controlplane.discovery.ServiceRegistrationCredentialService;
import com.autoapi.controlplane.persistence.ServiceRegistrationCredentialEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
public class ServiceDiscoveryManagementRouter {

  @Bean
  @Order(11)
  RouterFunction<ServerResponse> serviceDiscoveryRoutes(
      DiscoveredServiceService discoveredServiceService,
      ServiceInstanceService serviceInstanceService,
      ServiceRegistrationCredentialService credentialService,
      DiscoveredServiceRouteBindingService routeBindingService) {
    Handler handler =
        new Handler(
            discoveredServiceService,
            serviceInstanceService,
            credentialService,
            routeBindingService);
    return RouterFunctions.route()
        .path(
            "/api/v1",
            builder ->
                builder
                    .POST("/projects/{projectId}/services", handler::createService)
                    .GET("/projects/{projectId}/services", handler::listServices)
                    .GET("/projects/{projectId}/services/{serviceId}", handler::getService)
                    .PATCH("/projects/{projectId}/services/{serviceId}", handler::patchService)
                    .DELETE("/projects/{projectId}/services/{serviceId}", handler::deleteService)
                    .POST(
                        "/projects/{projectId}/services/{serviceId}/registration-credentials",
                        handler::createCredential)
                    .GET(
                        "/projects/{projectId}/services/{serviceId}/registration-credentials",
                        handler::listCredentials)
                    .GET("/services/{serviceId}/instances", handler::listInstances)
                    .GET("/services/{serviceId}/instances/{instanceId}", handler::getInstance)
                    .POST("/services/{serviceId}/instances/register", handler::registerInstance)
                    .POST(
                        "/services/{serviceId}/instances/{instanceId}/heartbeat",
                        handler::heartbeat)
                    .POST("/services/{serviceId}/instances/{instanceId}/drain", handler::drain)
                    .POST(
                        "/services/{serviceId}/instances/{instanceId}/deregister",
                        handler::deregister)
                    .PUT("/routes/{routeId}/discovered-service", handler::bindRoute)
                    .DELETE("/routes/{routeId}/discovered-service", handler::unbindRoute))
        .build();
  }

  static final class Handler {

    private final DiscoveredServiceService discoveredServiceService;
    private final ServiceInstanceService serviceInstanceService;
    private final ServiceRegistrationCredentialService credentialService;
    private final DiscoveredServiceRouteBindingService routeBindingService;

    Handler(
        DiscoveredServiceService discoveredServiceService,
        ServiceInstanceService serviceInstanceService,
        ServiceRegistrationCredentialService credentialService,
        DiscoveredServiceRouteBindingService routeBindingService) {
      this.discoveredServiceService = discoveredServiceService;
      this.serviceInstanceService = serviceInstanceService;
      this.credentialService = credentialService;
      this.routeBindingService = routeBindingService;
    }

    Mono<ServerResponse> createService(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return request
          .bodyToMono(CreateDiscoveredServiceRequest.class)
          .flatMap(
              body ->
                  discoveredServiceService.create(
                      projectId,
                      body.name(),
                      body.description(),
                      body.selectionStrategy(),
                      body.registrationMode(),
                      body.defaultScheme(),
                      body.defaultPort(),
                      body.consistentHashKey(),
                      body.consistentHashKeyName(),
                      body.metadata(),
                      body.enabled() == null || body.enabled()))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(DiscoveredServiceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listServices(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      return discoveredServiceService
          .list(projectId)
          .map(DiscoveredServiceResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getService(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID serviceId = uuid(request, "serviceId");
      return discoveredServiceService
          .get(projectId, serviceId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(DiscoveredServiceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> patchService(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID serviceId = uuid(request, "serviceId");
      return request
          .bodyToMono(PatchDiscoveredServiceRequest.class)
          .flatMap(
              body ->
                  discoveredServiceService.patch(
                      projectId,
                      serviceId,
                      body.name(),
                      body.description(),
                      body.enabled(),
                      body.selectionStrategy(),
                      body.registrationMode(),
                      body.defaultScheme(),
                      body.defaultPort(),
                      body.consistentHashKey(),
                      body.consistentHashKeyName(),
                      body.metadata()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(DiscoveredServiceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deleteService(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID serviceId = uuid(request, "serviceId");
      return discoveredServiceService
          .delete(projectId, serviceId)
          .then(ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createCredential(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID serviceId = uuid(request, "serviceId");
      return request
          .bodyToMono(CreateRegistrationCredentialRequest.class)
          .flatMap(body -> credentialService.create(projectId, serviceId, body.name()))
          .flatMap(
              created ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(CreatedRegistrationCredentialResponse.from(created)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listCredentials(ServerRequest request) {
      UUID projectId = uuid(request, "projectId");
      UUID serviceId = uuid(request, "serviceId");
      return credentialService
          .list(projectId, serviceId)
          .map(RegistrationCredentialResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listInstances(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      int limit = intParam(request, "limit", 100);
      int offset = intParam(request, "offset", 0);
      return serviceInstanceService
          .list(
              serviceId,
              request.queryParam("status").orElse(null),
              request.queryParam("zone").orElse(null),
              request.queryParam("region").orElse(null),
              request.queryParam("instanceId").orElse(null),
              limit,
              offset)
          .map(ServiceInstanceResponse::from)
          .collectList()
          .flatMap(list -> ServerResponse.ok().bodyValue(list))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getInstance(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      String instanceId = request.pathVariable("instanceId");
      return serviceInstanceService
          .get(serviceId, instanceId)
          .flatMap(entity -> ServerResponse.ok().bodyValue(ServiceInstanceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> registerInstance(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      return request
          .bodyToMono(ServiceInstanceService.RegisterInstanceRequest.class)
          .flatMap(body -> serviceInstanceService.register(serviceId, request, body))
          .flatMap(
              entity ->
                  ServerResponse.status(HttpStatus.CREATED)
                      .bodyValue(ServiceInstanceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> heartbeat(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      String instanceId = request.pathVariable("instanceId");
      return request
          .bodyToMono(ServiceInstanceService.HeartbeatRequest.class)
          .defaultIfEmpty(new ServiceInstanceService.HeartbeatRequest(null))
          .flatMap(body -> serviceInstanceService.heartbeat(serviceId, instanceId, request, body))
          .flatMap(entity -> ServerResponse.ok().bodyValue(ServiceInstanceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> drain(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      String instanceId = request.pathVariable("instanceId");
      return serviceInstanceService
          .drain(serviceId, instanceId, request)
          .flatMap(entity -> ServerResponse.ok().bodyValue(ServiceInstanceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> deregister(ServerRequest request) {
      UUID serviceId = uuid(request, "serviceId");
      String instanceId = request.pathVariable("instanceId");
      return serviceInstanceService
          .deregister(serviceId, instanceId, request)
          .flatMap(entity -> ServerResponse.ok().bodyValue(ServiceInstanceResponse.from(entity)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> bindRoute(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return request
          .bodyToMono(BindDiscoveredServiceRequest.class)
          .flatMap(body -> routeBindingService.bindDiscoveredService(routeId, body.serviceId()))
          .flatMap(entity -> ServerResponse.ok().bodyValue(Map.of("routeId", entity.id())))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> unbindRoute(ServerRequest request) {
      UUID routeId = uuid(request, "routeId");
      return routeBindingService
          .unbindDiscoveredService(routeId)
          .flatMap(entity -> ServerResponse.noContent().build())
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Mono<ServerResponse> error(ControlPlaneException ex) {
      return ServerResponse.status(ex.status())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "error",
                  Map.of(
                      "code", ex.code(),
                      "message", ex.getMessage())));
    }

    private static UUID uuid(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }

    private static int intParam(ServerRequest request, String name, int defaultValue) {
      return request.queryParam(name).map(Integer::parseInt).orElse(defaultValue);
    }
  }

  record CreateDiscoveredServiceRequest(
      String name,
      String description,
      String selectionStrategy,
      String registrationMode,
      String defaultScheme,
      Integer defaultPort,
      String consistentHashKey,
      String consistentHashKeyName,
      Map<String, String> metadata,
      Boolean enabled) {}

  record PatchDiscoveredServiceRequest(
      String name,
      String description,
      Boolean enabled,
      String selectionStrategy,
      String registrationMode,
      String defaultScheme,
      Integer defaultPort,
      String consistentHashKey,
      String consistentHashKeyName,
      Map<String, String> metadata) {}

  record CreateRegistrationCredentialRequest(String name) {}

  record BindDiscoveredServiceRequest(UUID serviceId) {}

  record RegistrationCredentialResponse(
      UUID id, UUID serviceId, String credentialId, String name, boolean enabled) {
    static RegistrationCredentialResponse from(ServiceRegistrationCredentialEntity entity) {
      return new RegistrationCredentialResponse(
          entity.id(), entity.serviceId(), entity.credentialId(), entity.name(), entity.enabled());
    }
  }
}
