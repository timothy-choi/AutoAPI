package com.autoapi.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public ErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Mono<Void> routeNotFound(ServerWebExchange exchange) {
    return write(exchange, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "No matching route was found");
  }

  public Mono<Void> methodNotAllowed(
      ServerWebExchange exchange, java.util.Set<org.springframework.http.HttpMethod> allowed) {
    exchange.getResponse().getHeaders().set("Allow", AllowHeaderFormatter.format(allowed));
    return write(
        exchange,
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "HTTP method is not allowed for this route");
  }

  public Mono<Void> upstreamUnavailable(ServerWebExchange exchange, Throwable cause) {
    return write(
        exchange,
        HttpStatus.BAD_GATEWAY,
        "UPSTREAM_UNAVAILABLE",
        "Upstream service is unavailable");
  }

  public Mono<Void> internalError(ServerWebExchange exchange, String message) {
    return write(
        exchange,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_GATEWAY_ERROR",
        "An internal gateway error occurred");
  }

  private Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String requestId = com.autoapi.middleware.RequestIdSupport.getRequestId(exchange);
    ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(code, message, requestId));
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      bytes =
          ("{\"error\":{\"code\":\"INTERNAL_GATEWAY_ERROR\",\"message\":\"Serialization failure\",\"requestId\":\""
                  + requestId
                  + "\"}}")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorResponse(ErrorBody error) {
    public record ErrorBody(String code, String message, String requestId) {}
  }
}
