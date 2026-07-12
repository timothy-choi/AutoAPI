package com.autoapi.gateway.security;

import com.autoapi.config.RouteConfig;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface GatewaySecurityEnforcer {

  Mono<Void> enforce(ServerWebExchange exchange, ActiveRuntimeBundle bundle, RouteConfig route);
}
