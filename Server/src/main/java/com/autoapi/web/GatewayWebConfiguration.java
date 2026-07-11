package com.autoapi.web;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.proxy.ProxyHandler;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;

@Configuration
public class GatewayWebConfiguration {

  @Bean
  @ConditionalOnBean(RuntimeConfigHolder.class)
  RuntimeConfig runtimeConfig(RuntimeConfigHolder holder) {
    return holder.config();
  }

  @Bean
  @Order(0)
  RouterFunction<ServerResponse> healthRoutes(GatewayReadiness readiness) {
    return RouterFunctions.route()
        .GET("/healthz", request -> ServerResponse.ok().bodyValue(java.util.Map.of("status", "UP")))
        .GET(
            "/readyz",
            request ->
                readiness
                    .isReady()
                    .flatMap(
                        ready ->
                            ready
                                ? ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(java.util.Map.of("status", "UP"))
                                : ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(java.util.Map.of("status", "DOWN")))
                    .onErrorResume(
                        ex ->
                            ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(java.util.Map.of("status", "DOWN"))))
        .build();
  }

  @Bean
  @Order(10)
  @ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
  WebFilter gatewayProxyFilter(
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      ProxyHandler proxyHandler,
      ErrorResponseWriter errorResponseWriter) {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getPath().pathWithinApplication().value();
      if (GatewayReservedPaths.isReservedPath(path)) {
        return chain.filter(exchange);
      }
      ActiveRuntimeBundle bundle = activeRuntimeConfigHolder.getActiveForRequest();
      if (bundle == null) {
        return errorResponseWriter.gatewayNotReady(exchange);
      }
      exchange.getAttributes().put(GatewayAttributes.ACTIVE_RUNTIME_BUNDLE, bundle);
      exchange.getAttributes().put(GatewayAttributes.RUNTIME_CONFIG, bundle.runtimeConfig());
      return proxyHandler.handle(exchange);
    };
  }
}
