package com.autoapi.controlplane.managementauth;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public final class ManagementSecurityContext {

  public static final String PRINCIPAL_ATTRIBUTE = "autoapi.management.principal";
  public static final String CONTEXT_KEY = PRINCIPAL_ATTRIBUTE;

  private ManagementSecurityContext() {}

  public static void setPrincipal(ServerWebExchange exchange, ManagementPrincipal principal) {
    exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
  }

  public static ManagementPrincipal principal(ServerWebExchange exchange) {
    Object value = exchange.getAttributes().get(PRINCIPAL_ATTRIBUTE);
    if (value instanceof ManagementPrincipal principal) {
      return principal;
    }
    return null;
  }

  public static Mono<ManagementPrincipal> requirePrincipal(ServerWebExchange exchange) {
    ManagementPrincipal principal = principal(exchange);
    if (principal != null) {
      return Mono.just(principal);
    }
    return Mono.error(
        com.autoapi.controlplane.api.ControlPlaneException.authenticationRequired(
            "Authentication is required"));
  }

  public static Context withPrincipal(Context context, ManagementPrincipal principal) {
    return context.put(CONTEXT_KEY, principal);
  }

  public static Mono<ManagementPrincipal> current() {
    return Mono.deferContextual(
        ctx ->
            ctx.hasKey(CONTEXT_KEY)
                ? Mono.just(ctx.get(CONTEXT_KEY))
                : Mono.error(
                    com.autoapi.controlplane.api.ControlPlaneException.authenticationRequired(
                        "Authentication is required")));
  }
}
