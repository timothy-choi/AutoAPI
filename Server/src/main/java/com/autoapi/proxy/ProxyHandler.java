package com.autoapi.proxy;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.routing.RouteMatchResult;
import com.autoapi.routing.RouteMatcher;
import com.autoapi.web.ErrorResponseWriter;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class ProxyHandler {

  private final RouteMatcher routeMatcher;
  private final WebClient webClient;
  private final ErrorResponseWriter errorWriter;

  public ProxyHandler(RuntimeConfig runtimeConfig, ErrorResponseWriter errorWriter) {
    this.routeMatcher = new RouteMatcher(runtimeConfig);
    this.errorWriter = errorWriter;
    this.webClient =
        WebClient.builder()
            .clientConnector(
                new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create()))
            .build();
  }

  public Mono<Void> handle(ServerWebExchange exchange) {
    RuntimeConfig config = exchange.getAttribute(GatewayAttributes.RUNTIME_CONFIG);
    if (config == null) {
      return errorWriter.internalError(exchange, "Runtime configuration unavailable");
    }

    ServerHttpRequest request = exchange.getRequest();
    String requestId = RequestIdSupport.getRequestId(exchange);
    HttpMethod method = request.getMethod();
    if (method == null) {
      return errorWriter.internalError(exchange, "Missing HTTP method");
    }

    RouteMatchResult match =
        routeMatcher.match(
            Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.HOST)).orElse(""),
            request.getPath().pathWithinApplication().value(),
            method);

    if (!match.isMatched()) {
      if (match.reason() == com.autoapi.routing.RouteNotFoundReason.METHOD_NOT_ALLOWED) {
        return errorWriter.methodNotAllowed(exchange, match.allowedMethods());
      }
      return errorWriter.routeNotFound(exchange);
    }

    RouteConfig route = match.matchedRoute().orElseThrow();
    exchange.getAttributes().put(GatewayAttributes.MATCHED_ROUTE_ID, route.id());
    exchange
        .getAttributes()
        .put(GatewayAttributes.UPSTREAM_AUTHORITY, route.upstream().url().getAuthority());

    URI targetUri = buildUpstreamUri(route, request);
    return forward(exchange, route, targetUri, requestId);
  }

  private URI buildUpstreamUri(RouteConfig route, ServerHttpRequest request) {
    URI base = route.upstream().url();
    String path = request.getURI().getRawPath();
    String query = request.getURI().getRawQuery();
    StringBuilder builder = new StringBuilder();
    builder.append(base.getScheme()).append("://").append(base.getAuthority()).append(path);
    if (query != null && !query.isEmpty()) {
      builder.append('?').append(query);
    }
    return URI.create(builder.toString());
  }

  private Mono<Void> forward(
      ServerWebExchange exchange, RouteConfig route, URI targetUri, String requestId) {
    ServerHttpRequest incoming = exchange.getRequest();

    return webClient
        .method(incoming.getMethod())
        .uri(targetUri)
        .headers(
            headers -> {
              headers.addAll(incoming.getHeaders());
              HopByHopHeaders.sanitizeClientRequestHeaders(headers);
              headers.set(RequestIdSupport.HEADER, requestId);
              headers.set("X-Forwarded-Host", hostHeaderValue(incoming));
              headers.set(
                  "X-Forwarded-Proto",
                  incoming.getURI().getScheme() != null ? incoming.getURI().getScheme() : "http");
              String remote =
                  Optional.ofNullable(incoming.getRemoteAddress())
                      .map(addr -> addr.getAddress().getHostAddress())
                      .orElse("127.0.0.1");
              headers.set("X-Forwarded-For", remote);
            })
        .body(BodyInserters.fromDataBuffers(incoming.getBody()))
        .exchangeToMono(response -> writeUpstreamResponse(exchange, response, requestId))
        .onErrorResume(
            WebClientRequestException.class, ex -> errorWriter.upstreamUnavailable(exchange, ex));
  }

  private static String hostHeaderValue(ServerHttpRequest request) {
    String host = request.getHeaders().getFirst(HttpHeaders.HOST);
    return host != null ? host : "localhost";
  }

  private Mono<Void> writeUpstreamResponse(
      ServerWebExchange exchange, ClientResponse response, String requestId) {
    exchange.getResponse().setStatusCode(response.statusCode());
    HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
    responseHeaders.addAll(response.headers().asHttpHeaders());
    HopByHopHeaders.sanitizeUpstreamResponseHeaders(responseHeaders);
    responseHeaders.set(RequestIdSupport.HEADER, requestId);
    return exchange
        .getResponse()
        .writeWith(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
  }
}
