package com.autoapi.web;

import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.proxy.ProxyHandler;
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
  WebFilter gatewayProxyFilter(RuntimeConfig runtimeConfig, ProxyHandler proxyHandler) {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getPath().pathWithinApplication().value();
      if (GatewayReservedPaths.isReservedPath(path)) {
        return chain.filter(exchange);
      }
      exchange.getAttributes().put(GatewayAttributes.RUNTIME_CONFIG, runtimeConfig);
      return proxyHandler.handle(exchange);
    };
  }
}
