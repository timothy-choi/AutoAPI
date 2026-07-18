package com.autoapi.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ErrorResponseWriter {

  private static final Logger log = LoggerFactory.getLogger(ErrorResponseWriter.class);

  static final byte[] SERIALIZATION_FALLBACK_JSON =
      """
      {"error":{"code":"INTERNAL_GATEWAY_ERROR","message":"An internal gateway error occurred","requestId":"unavailable"}}
      """
          .trim()
          .getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper objectMapper;

  public ErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Mono<Void> gatewayNotReady(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "GATEWAY_NOT_READY",
        "No runtime configuration has been activated");
  }

  public Mono<Void> routeNotFound(ServerWebExchange exchange) {
    return write(exchange, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "No matching route was found");
  }

  public Mono<Void> methodNotAllowed(
      ServerWebExchange exchange, java.util.Set<org.springframework.http.HttpMethod> allowed) {
    if (!exchange.getResponse().isCommitted()) {
      exchange.getResponse().getHeaders().set("Allow", AllowHeaderFormatter.format(allowed));
    }
    return write(
        exchange,
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "HTTP method is not allowed for this route");
  }

  public Mono<Void> upstreamUnavailable(ServerWebExchange exchange, Throwable cause) {
    logProxyFailure(exchange, cause);
    return write(
        exchange,
        HttpStatus.BAD_GATEWAY,
        "UPSTREAM_UNAVAILABLE",
        "Upstream service is unavailable");
  }

  public Mono<Void> upstreamTimeout(ServerWebExchange exchange, Throwable cause) {
    logProxyFailure(exchange, cause);
    return write(
        exchange, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "Upstream service timed out");
  }

  public Mono<Void> internalError(ServerWebExchange exchange, Throwable cause) {
    logProxyFailure(exchange, cause);
    return write(
        exchange,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_GATEWAY_ERROR",
        "An internal gateway error occurred");
  }

  public Mono<Void> invalidApiKey(ServerWebExchange exchange) {
    if (!exchange.getResponse().isCommitted()) {
      exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
    }
    return write(
        exchange, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "A valid API key is required");
  }

  public Mono<Void> rateLimitExceeded(
      ServerWebExchange exchange,
      com.autoapi.gateway.redis.GatewayRateLimitService.RateLimitDecision decision) {
    if (!exchange.getResponse().isCommitted()) {
      exchange.getResponse().getHeaders().set("RateLimit-Limit", String.valueOf(decision.limit()));
      exchange.getResponse().getHeaders().set("RateLimit-Remaining", "0");
      exchange
          .getResponse()
          .getHeaders()
          .set("RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
      if (decision.retryAfterSeconds() > 0) {
        exchange
            .getResponse()
            .getHeaders()
            .set("Retry-After", String.valueOf(decision.retryAfterSeconds()));
      }
    }
    return write(
        exchange,
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMIT_EXCEEDED",
        "The request rate limit has been exceeded");
  }

  public Mono<Void> rateLimitDependencyUnavailable(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "RATE_LIMIT_DEPENDENCY_UNAVAILABLE",
        "Rate limiting is temporarily unavailable");
  }

  public Mono<Void> noAvailableUpstream(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "NO_AVAILABLE_UPSTREAM",
        "No upstream target is available");
  }

  public Mono<Void> circuitBreakerOpen(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "CIRCUIT_BREAKER_OPEN",
        "Upstream circuit breaker is open");
  }

  public Mono<Void> noAvailableTrafficDestination(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "NO_AVAILABLE_TRAFFIC_DESTINATION",
        "No configured traffic destination is available");
  }

  public Mono<Void> trafficSplitConfigurationUnavailable(ServerWebExchange exchange) {
    return write(
        exchange,
        HttpStatus.SERVICE_UNAVAILABLE,
        "TRAFFIC_SPLIT_CONFIGURATION_UNAVAILABLE",
        "Traffic split configuration is unavailable");
  }

  private void logProxyFailure(ServerWebExchange exchange, Throwable cause) {
    String requestId = com.autoapi.middleware.RequestIdSupport.getRequestId(exchange);
    log.warn(
        "requestId={} routeId={} upstream={} errorType={} message={}",
        requestId,
        exchange.getAttribute(com.autoapi.proxy.GatewayAttributes.MATCHED_ROUTE_ID),
        exchange.getAttribute(com.autoapi.proxy.GatewayAttributes.UPSTREAM_AUTHORITY),
        cause.getClass().getSimpleName(),
        safeSummary(cause));
    if (log.isDebugEnabled()) {
      log.debug("requestId={} controlled proxy error detail", requestId, cause);
    }
  }

  private static String safeSummary(Throwable cause) {
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    return message.length() > 200 ? message.substring(0, 200) : message;
  }

  private Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    if (exchange.getResponse().isCommitted()) {
      log.warn(
          "requestId={} response already committed; cannot write error code={}",
          com.autoapi.middleware.RequestIdSupport.getRequestId(exchange),
          code);
      return Mono.empty();
    }
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String requestId = com.autoapi.middleware.RequestIdSupport.getRequestId(exchange);
    byte[] bytes = serializeErrorBody(code, message, requestId);
    var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  byte[] serializeErrorBody(String code, String message, String requestId) {
    ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(code, message, requestId));
    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize error response; using fallback JSON", e);
      return SERIALIZATION_FALLBACK_JSON;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorResponse(ErrorBody error) {
    public record ErrorBody(String code, String message, String requestId) {}
  }
}
