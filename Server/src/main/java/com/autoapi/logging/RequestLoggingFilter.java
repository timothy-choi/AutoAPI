package com.autoapi.logging;

import com.autoapi.proxy.GatewayAttributes;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

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

    long startedAtNanos = System.nanoTime();
    exchange.getAttributes().put(GatewayAttributes.REQUEST_START_NANOS, startedAtNanos);

    return chain
        .filter(exchange)
        .doFinally(signalType -> logCompletionSafely(exchange, signalType, startedAtNanos, path));
  }

  private void logCompletionSafely(
      ServerWebExchange exchange, SignalType signalType, long startedAtNanos, String path) {
    try {
      logCompletion(exchange, signalType, startedAtNanos, path);
    } catch (RuntimeException loggingFailure) {
      log.warn("Request completion logging failed: {}", loggingFailure.toString());
    }
  }

  private void logCompletion(
      ServerWebExchange exchange, SignalType signalType, long startedAtNanos, String path) {
    long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAtNanos);
    String requestId = requestId(exchange);
    String host = host(exchange);
    Object routeId = exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID);
    Object upstream = exchange.getAttribute(GatewayAttributes.UPSTREAM_AUTHORITY);
    HttpStatusCode status = exchange.getResponse().getStatusCode();
    int statusCode = status == null ? 0 : status.value();

    log.info(
        "request completed requestId={} method={} host={} path={} routeId={} upstream={} status={} durationMicros={} signal={}",
        requestId,
        exchange.getRequest().getMethod(),
        host,
        path,
        routeId != null ? routeId : "-",
        upstream != null ? upstream : "-",
        statusCode,
        elapsedMicros,
        signalType);
  }

  private static String requestId(ServerWebExchange exchange) {
    Object rawRequestId = exchange.getAttribute(GatewayAttributes.REQUEST_ID);
    return Objects.toString(rawRequestId, "");
  }

  private static String host(ServerWebExchange exchange) {
    String headerHost = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
    return headerHost != null ? headerHost : "";
  }
}
