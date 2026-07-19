package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.middleware.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
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
public class ManagementAuthRouter {

  @Bean
  @Order(4)
  RouterFunction<ServerResponse> managementAuthRoutes(
      BootstrapService bootstrapService,
      OrganizationService organizationService,
      ServiceAccountService serviceAccountService,
      RoleBindingService roleBindingService,
      ManagementCredentialService credentialService,
      ManagementAuthorizationService authorizationService,
      ObjectMapper objectMapper) {
    Handler handler =
        new Handler(
            bootstrapService,
            organizationService,
            serviceAccountService,
            roleBindingService,
            credentialService,
            authorizationService,
            objectMapper);
    return RouterFunctions.route()
        .path(
            "/api/v1/management",
            builder ->
                builder
                    .POST("/bootstrap", handler::bootstrap)
                    .GET("/auth/me", handler::authMe)
                    .POST("/organizations", handler::createOrganization)
                    .GET("/organizations", handler::listOrganizations)
                    .GET("/organizations/{organizationId}", handler::getOrganization)
                    .POST(
                        "/organizations/{organizationId}/role-bindings", handler::createOrgBinding)
                    .GET("/organizations/{organizationId}/role-bindings", handler::listOrgBindings)
                    .DELETE(
                        "/organizations/{organizationId}/role-bindings/{bindingId}",
                        handler::revokeOrgBinding)
                    .POST(
                        "/organizations/{organizationId}/service-accounts",
                        handler::createServiceAccount)
                    .GET(
                        "/organizations/{organizationId}/service-accounts",
                        handler::listServiceAccounts)
                    .GET(
                        "/organizations/{organizationId}/service-accounts/{serviceAccountId}",
                        handler::getServiceAccount)
                    .DELETE(
                        "/organizations/{organizationId}/service-accounts/{serviceAccountId}",
                        handler::disableServiceAccount)
                    .POST("/projects/{projectId}/role-bindings", handler::createProjectBinding)
                    .GET("/projects/{projectId}/role-bindings", handler::listProjectBindings)
                    .DELETE(
                        "/projects/{projectId}/role-bindings/{bindingId}",
                        handler::revokeProjectBinding)
                    .POST(
                        "/service-accounts/{serviceAccountId}/credentials",
                        handler::createCredential)
                    .GET(
                        "/service-accounts/{serviceAccountId}/credentials",
                        handler::listCredentials)
                    .GET(
                        "/service-accounts/{serviceAccountId}/credentials/{credentialId}",
                        handler::getCredential)
                    .POST(
                        "/service-accounts/{serviceAccountId}/credentials/{credentialId}/rotate",
                        handler::rotateCredential)
                    .POST(
                        "/service-accounts/{serviceAccountId}/credentials/{credentialId}/revoke",
                        handler::revokeCredential))
        .build();
  }

  static final class Handler {
    private final BootstrapService bootstrapService;
    private final OrganizationService organizationService;
    private final ServiceAccountService serviceAccountService;
    private final RoleBindingService roleBindingService;
    private final ManagementCredentialService credentialService;
    private final ManagementAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    Handler(
        BootstrapService bootstrapService,
        OrganizationService organizationService,
        ServiceAccountService serviceAccountService,
        RoleBindingService roleBindingService,
        ManagementCredentialService credentialService,
        ManagementAuthorizationService authorizationService,
        ObjectMapper objectMapper) {
      this.bootstrapService = bootstrapService;
      this.organizationService = organizationService;
      this.serviceAccountService = serviceAccountService;
      this.roleBindingService = roleBindingService;
      this.credentialService = credentialService;
      this.authorizationService = authorizationService;
      this.objectMapper = objectMapper;
    }

    Mono<ServerResponse> bootstrap(ServerRequest request) {
      return principal(request)
          .flatMap(
              principal ->
                  bootstrapService
                      .initialize(principal, eventContext(request))
                      .flatMap(
                          result ->
                              ServerResponse.ok()
                                  .cacheControl(CacheControl.noStore())
                                  .header("Pragma", "no-cache")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .bodyValue(
                                      Map.of(
                                          "organizationId",
                                          result.organization().id().toString(),
                                          "serviceAccountId",
                                          result.serviceAccount().id().toString(),
                                          "credentialId",
                                          result.credential().id().toString(),
                                          "token",
                                          result.token()))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> authMe(ServerRequest request) {
      return principal(request)
          .flatMap(
              principal ->
                  ServerResponse.ok()
                      .contentType(MediaType.APPLICATION_JSON)
                      .bodyValue(
                          Map.of(
                              "principalType",
                              principal.principalType().name(),
                              "principalId",
                              principal.principalId().toString(),
                              "displayName",
                              principal.displayName(),
                              "organizationId",
                              principal.organizationId() == null
                                  ? null
                                  : principal.organizationId().toString(),
                              "roles",
                              principal.roles().stream().map(Enum::name).sorted().toList(),
                              "credentialScopes",
                              principal.credentialScopes())))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createOrganization(ServerRequest request) {
      return parseBody(request, CreateOrganizationRequest.class)
          .zipWith(principal(request))
          .flatMap(
              tuple ->
                  organizationService
                      .create(
                          tuple.getT2(),
                          tuple.getT1().name(),
                          tuple.getT1().slug(),
                          tuple.getT1().description())
                      .flatMap(org -> jsonOk(org)))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listOrganizations(ServerRequest request) {
      return organizationService.list().collectList().flatMap(this::jsonOk);
    }

    Mono<ServerResponse> getOrganization(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      return organizationService.get(organizationId).flatMap(this::jsonOk);
    }

    Mono<ServerResponse> createOrgBinding(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      return parseBody(request, CreateRoleBindingRequest.class)
          .zipWith(principal(request))
          .flatMap(
              tuple ->
                  roleBindingService
                      .createOrganizationBinding(
                          tuple.getT2(),
                          organizationId,
                          PrincipalType.parse(tuple.getT1().principalType()),
                          UUID.fromString(tuple.getT1().principalId()),
                          BuiltInRole.parse(tuple.getT1().role()),
                          tuple.getT1().expiresAt(),
                          eventContext(request))
                      .flatMap(this::jsonOk))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listOrgBindings(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      return roleBindingService
          .listOrganizationBindings(organizationId)
          .collectList()
          .flatMap(this::jsonOk);
    }

    Mono<ServerResponse> revokeOrgBinding(ServerRequest request) {
      UUID bindingId = uuidPath(request, "bindingId");
      return principal(request)
          .flatMap(
              principal -> roleBindingService.revoke(principal, bindingId, eventContext(request)))
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createServiceAccount(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      return parseBody(request, CreateServiceAccountRequest.class)
          .zipWith(principal(request))
          .flatMap(
              tuple ->
                  serviceAccountService
                      .create(
                          tuple.getT2(),
                          organizationId,
                          tuple.getT1().projectId(),
                          tuple.getT1().name(),
                          tuple.getT1().description(),
                          eventContext(request))
                      .flatMap(this::jsonOk))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listServiceAccounts(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      return serviceAccountService.list(organizationId, null).collectList().flatMap(this::jsonOk);
    }

    Mono<ServerResponse> getServiceAccount(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      return serviceAccountService.get(organizationId, serviceAccountId).flatMap(this::jsonOk);
    }

    Mono<ServerResponse> disableServiceAccount(ServerRequest request) {
      UUID organizationId = uuidPath(request, "organizationId");
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      return serviceAccountService
          .disable(organizationId, serviceAccountId, eventContext(request))
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createProjectBinding(ServerRequest request) {
      UUID projectId = uuidPath(request, "projectId");
      return parseBody(request, CreateRoleBindingRequest.class)
          .zipWith(principal(request))
          .flatMap(
              tuple ->
                  roleBindingService.createProjectBinding(
                      tuple.getT2(),
                      tuple.getT2().organizationId(),
                      projectId,
                      PrincipalType.parse(tuple.getT1().principalType()),
                      UUID.fromString(tuple.getT1().principalId()),
                      BuiltInRole.parse(tuple.getT1().role()),
                      tuple.getT1().expiresAt(),
                      eventContext(request)))
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listProjectBindings(ServerRequest request) {
      UUID projectId = uuidPath(request, "projectId");
      return principal(request)
          .flatMap(
              principal ->
                  authorizationService
                      .requireProjectRead(principal, projectId)
                      .then(
                          roleBindingService
                              .listProjectBindings(principal.organizationId(), projectId)
                              .collectList()))
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> revokeProjectBinding(ServerRequest request) {
      UUID bindingId = uuidPath(request, "bindingId");
      return principal(request)
          .flatMap(
              principal -> roleBindingService.revoke(principal, bindingId, eventContext(request)))
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> createCredential(ServerRequest request) {
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      return parseBody(request, CreateCredentialRequest.class)
          .zipWith(principal(request))
          .flatMap(
              tuple ->
                  credentialService
                      .create(
                          tuple.getT2(),
                          serviceAccountId,
                          tuple.getT1().name(),
                          tuple.getT1().description(),
                          ManagementPermission.parseAll(tuple.getT1().scopes()),
                          tuple.getT1().expiresAt(),
                          eventContext(request))
                      .flatMap(
                          created ->
                              ServerResponse.ok()
                                  .cacheControl(CacheControl.noStore())
                                  .header("Pragma", "no-cache")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .bodyValue(
                                      Map.of(
                                          "credential",
                                          redactCredential(created.credential()),
                                          "token",
                                          created.token()))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> listCredentials(ServerRequest request) {
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      return principal(request)
          .flatMap(
              principal ->
                  credentialService
                      .list(principal, serviceAccountId)
                      .map(this::redactCredential)
                      .collectList())
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> getCredential(ServerRequest request) {
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      UUID credentialId = uuidPath(request, "credentialId");
      return principal(request)
          .flatMap(principal -> credentialService.get(principal, serviceAccountId, credentialId))
          .map(this::redactCredential)
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> rotateCredential(ServerRequest request) {
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      UUID credentialId = uuidPath(request, "credentialId");
      return principal(request)
          .flatMap(
              principal ->
                  credentialService
                      .rotate(principal, serviceAccountId, credentialId, eventContext(request))
                      .flatMap(
                          created ->
                              ServerResponse.ok()
                                  .cacheControl(CacheControl.noStore())
                                  .header("Pragma", "no-cache")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .bodyValue(
                                      Map.of(
                                          "credential",
                                          redactCredential(created.credential()),
                                          "token",
                                          created.token()))))
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    Mono<ServerResponse> revokeCredential(ServerRequest request) {
      UUID serviceAccountId = uuidPath(request, "serviceAccountId");
      UUID credentialId = uuidPath(request, "credentialId");
      return principal(request)
          .flatMap(
              principal ->
                  credentialService.revoke(
                      principal, serviceAccountId, credentialId, eventContext(request)))
          .map(this::redactCredential)
          .flatMap(this::jsonOk)
          .onErrorResume(ControlPlaneException.class, this::error);
    }

    private Map<String, Object> redactCredential(
        com.autoapi.controlplane.persistence.ManagementAccessCredentialEntity credential) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", credential.id().toString());
      map.put("publicTokenId", credential.publicTokenId());
      map.put("name", credential.name());
      map.put("description", credential.description());
      map.put("scopes", authorizationService.decodeScopes(credential.scopes()));
      map.put("status", credential.status());
      map.put("createdAt", credential.createdAt());
      map.put("expiresAt", credential.expiresAt());
      map.put("lastUsedAt", credential.lastUsedAt());
      map.put("revokedAt", credential.revokedAt());
      return map;
    }

    private Mono<ManagementPrincipal> principal(ServerRequest request) {
      ManagementPrincipal principal = ManagementSecurityContext.principal(request.exchange());
      if (principal == null) {
        return Mono.error(
            ControlPlaneException.authenticationRequired("Authentication is required"));
      }
      return Mono.just(principal);
    }

    private EventContext eventContext(ServerRequest request) {
      String requestId = request.headers().firstHeader(RequestIdFilter.HEADER);
      ManagementPrincipal principal = ManagementSecurityContext.principal(request.exchange());
      return EventContext.fromPrincipal(requestId, principal);
    }

    private <T> Mono<T> parseBody(ServerRequest request, Class<T> type) {
      return request
          .bodyToMono(type)
          .switchIfEmpty(
              Mono.error(ControlPlaneException.invalidRequest("Request body is required")));
    }

    private Mono<ServerResponse> jsonOk(Object body) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }

    private UUID uuidPath(ServerRequest request, String name) {
      return UUID.fromString(request.pathVariable(name));
    }

    private Mono<ServerResponse> error(ControlPlaneException ex) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("code", ex.code());
      body.put("message", ex.getMessage());
      return ServerResponse.status(ex.status())
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body);
    }
  }

  record CreateOrganizationRequest(String name, String slug, String description) {}

  record CreateRoleBindingRequest(
      String principalType, String principalId, String role, OffsetDateTime expiresAt) {}

  record CreateServiceAccountRequest(UUID projectId, String name, String description) {}

  record CreateCredentialRequest(
      String name, String description, List<String> scopes, OffsetDateTime expiresAt) {}
}
