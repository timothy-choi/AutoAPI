package com.autoapi.logging;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Legacy filter retained for ordering compatibility. Structured request completion logging is
 * handled by {@link com.autoapi.gateway.observability.GatewayObservabilityFilter}.
 */
@Component
@Order(1)
public class RequestLoggingFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return chain.filter(exchange);
  }
}
