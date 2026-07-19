package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.middleware.RequestIdFilter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(0)
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
    ManagementAuthenticationService.class)
public class ManagementSecurityWebFilter implements WebFilter {

  private static final Pattern PROJECT_PATH =
      Pattern.compile("^/api/v1/projects/([0-9a-fA-F-]{36})(/.*)?$");
  private static final Pattern MANAGEMENT_PROJECT_PATH =
      Pattern.compile("^/api/v1/management/projects/([0-9a-fA-F-]{36})(/.*)?$");
  private static final Pattern ORGANIZATION_PATH =
      Pattern.compile("^/api/v1/management/organizations/([0-9a-fA-F-]{36})(/.*)?$");

  private final ManagementAuthProperties properties;
  private final ManagementAuthenticationService authenticationService;
  private final ManagementAuthorizationService authorizationService;
  private final ManagementEndpointPolicyRegistry endpointPolicies;

  public ManagementSecurityWebFilter(
      ManagementAuthProperties properties,
      ManagementAuthenticationService authenticationService,
      ManagementAuthorizationService authorizationService,
      ManagementEndpointPolicyRegistry endpointPolicies) {
    this.properties = properties;
    this.authenticationService = authenticationService;
    this.authorizationService = authorizationService;
    this.endpointPolicies = endpointPolicies;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!properties.enabled() || properties.security().allowUnauthenticatedDevelopment()) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getPath().value();
    if (isPublicPath(path, exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }
    if (!isManagementApiPath(path)) {
      return chain.filter(exchange);
    }
    String resolvedRequestId =
        (String) exchange.getAttributes().get(com.autoapi.proxy.GatewayAttributes.REQUEST_ID);
    if (resolvedRequestId == null) {
      resolvedRequestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER);
    }
    final String requestId = resolvedRequestId;
    return resolveBearer(exchange)
        .flatMap(token -> authenticationService.authenticateBearerToken(token, requestId))
        .flatMap(
            principal -> {
              ManagementSecurityContext.setPrincipal(exchange, principal);
              return authorize(exchange, principal)
                  .then(chain.filter(exchange))
                  .contextWrite(ctx -> ManagementSecurityContext.withPrincipal(ctx, principal));
            })
        .onErrorResume(ControlPlaneException.class, ex -> writeError(exchange, ex));
  }

  private Mono<Void> authorize(ServerWebExchange exchange, ManagementPrincipal principal) {
    String path = exchange.getRequest().getPath().value();
    HttpMethod method = exchange.getRequest().getMethod();
    ManagementEndpointPolicyRegistry.EndpointPolicy policy = endpointPolicies.resolve(method, path);
    if (policy == null) {
      return Mono.error(
          ControlPlaneException.forbidden("No authorization policy registered for this endpoint"));
    }
    if (principal.principalType() == PrincipalType.BOOTSTRAP_ADMIN) {
      return Mono.empty();
    }
    var matcher = PROJECT_PATH.matcher(path);
    if (matcher.matches()) {
      UUID projectId = UUID.fromString(matcher.group(1));
      return authorizationService.requireProjectPermission(
          principal, projectId, policy.permission());
    }
    var managementProjectMatcher = MANAGEMENT_PROJECT_PATH.matcher(path);
    if (managementProjectMatcher.matches()) {
      UUID projectId = UUID.fromString(managementProjectMatcher.group(1));
      return authorizationService.requireProjectPermission(
          principal, projectId, policy.permission());
    }
    var orgMatcher = ORGANIZATION_PATH.matcher(path);
    if (orgMatcher.matches()) {
      UUID organizationId = UUID.fromString(orgMatcher.group(1));
      return authorizationService.requireOrganizationPermission(
          principal, organizationId, policy.permission());
    }
    if (policy.organizationScope()) {
      UUID organizationId = principal.organizationId();
      if (organizationId == null) {
        return Mono.error(ControlPlaneException.forbidden("Organization context is required"));
      }
      return authorizationService.requireOrganizationPermission(
          principal, organizationId, policy.permission());
    }
    return Mono.empty();
  }

  private Mono<String> resolveBearer(ServerWebExchange exchange) {
    List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
    if (authHeaders == null || authHeaders.isEmpty()) {
      return Mono.error(
          ControlPlaneException.authenticationRequired("Authorization header is required"));
    }
    if (authHeaders.size() > 1) {
      return Mono.error(
          ControlPlaneException.authenticationRequired("Conflicting Authorization headers"));
    }
    String header = authHeaders.getFirst();
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return Mono.error(ControlPlaneException.authenticationRequired("Bearer token is required"));
    }
    String token = header.substring(7).trim();
    if (token.isBlank()) {
      return Mono.error(ControlPlaneException.authenticationRequired("Bearer token is required"));
    }
    return Mono.just(token);
  }

  private static boolean isManagementApiPath(String path) {
    return "/api/v1".equals(path) || path.startsWith("/api/v1/");
  }

  private static boolean isPublicPath(String path, HttpMethod method) {
    if ("/healthz".equals(path) || "/readyz".equals(path)) {
      return true;
    }
    if (path.startsWith("/actuator/health")) {
      return true;
    }
    if (path.startsWith("/api/v1/gateway-config")) {
      return true;
    }
    if (HttpMethod.POST.equals(method) && "/api/v1/gateways/register".equals(path)) {
      return true;
    }
    if (HttpMethod.POST.equals(method) && path.matches("^/api/v1/gateways/[^/]+/heartbeat$")) {
      return true;
    }
    if (HttpMethod.POST.equals(method) && path.matches("^/api/v1/gateways/[^/]+/config-status$")) {
      return true;
    }
    if (HttpMethod.POST.equals(method)
        && path.matches("^/api/v1/services/[^/]+/instances/register$")) {
      return true;
    }
    if (HttpMethod.POST.equals(method)
        && path.matches("^/api/v1/services/[^/]+/instances/[^/]+/heartbeat$")) {
      return true;
    }
    return false;
  }

  private Mono<Void> writeError(ServerWebExchange exchange, ControlPlaneException ex) {
    exchange.getResponse().setStatusCode(ex.status());
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String body =
        String.format(
            Locale.ROOT,
            "{\"code\":\"%s\",\"message\":\"%s\"}",
            ex.code(),
            escapeJson(ex.getMessage()));
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
