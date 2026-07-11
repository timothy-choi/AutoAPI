package com.autoapi.logging;

import com.autoapi.proxy.GatewayAttributes;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(1)
public class RequestLoggingFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if ("/healthz".equals(path) || "/readyz".equals(path)) {
      return chain.filter(exchange);
    }

    long start = System.nanoTime();
    exchange.getAttributes().put(GatewayAttributes.REQUEST_START_NANOS, start);

    return chain
        .filter(exchange)
        .doFinally(
            signal -> {
              long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
              String requestId =
                  String.valueOf(exchange.getAttribute(GatewayAttributes.REQUEST_ID));
              String host =
                  String.valueOf(exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
              Object routeId = exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID);
              Object upstream = exchange.getAttribute(GatewayAttributes.UPSTREAM_AUTHORITY);
              var status = exchange.getResponse().getStatusCode();
              log.info(
                  "requestId={} method={} host={} path={} routeId={} upstream={} status={} durationMs={}",
                  requestId,
                  exchange.getRequest().getMethod(),
                  host,
                  path,
                  routeId != null ? routeId : "-",
                  upstream != null ? upstream : "-",
                  status != null ? status.value() : 0,
                  durationMs);
            });
  }
}
