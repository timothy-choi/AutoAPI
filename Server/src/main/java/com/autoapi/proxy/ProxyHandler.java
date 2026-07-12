package com.autoapi.proxy;

import com.autoapi.config.HostNormalizer;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.security.GatewaySecurityEnforcer;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.routing.RouteMatchResult;
import com.autoapi.routing.RouteMatcher;
import com.autoapi.routing.RouteNotFoundReason;
import com.autoapi.web.ErrorResponseWriter;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

  private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

  private static final GatewaySecurityEnforcer NOOP_SECURITY =
      (exchange, bundle, route) -> Mono.empty();

  private final WebClient webClient;
  private final ErrorResponseWriter errorWriter;
  private final GatewaySecurityEnforcer securityPipeline;

  public ProxyHandler(
      ErrorResponseWriter errorWriter, ObjectProvider<GatewaySecurityEnforcer> securityPipeline) {
    this.errorWriter = errorWriter;
    this.securityPipeline = securityPipeline.getIfAvailable(() -> NOOP_SECURITY);
    this.webClient =
        WebClient.builder()
            .clientConnector(
                new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create()))
            .build();
  }

  public Mono<Void> handle(ServerWebExchange exchange) {
    ActiveRuntimeBundle bundle = exchange.getAttribute(GatewayAttributes.ACTIVE_RUNTIME_BUNDLE);
    RuntimeConfig config = exchange.getAttribute(GatewayAttributes.RUNTIME_CONFIG);
    if (bundle == null || config == null) {
      return errorWriter.gatewayNotReady(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String requestId = RequestIdSupport.getRequestId(exchange);
    HttpMethod method = request.getMethod();
    if (method == null) {
      return errorWriter.internalError(exchange, new IllegalStateException("Missing HTTP method"));
    }

    RouteMatcher routeMatcher = new RouteMatcher(config);
    RouteMatchResult match =
        routeMatcher.match(
            Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.HOST)).orElse(""),
            request.getPath().pathWithinApplication().value(),
            method);

    if (!match.isMatched()) {
      if (match.reason() == RouteNotFoundReason.METHOD_NOT_ALLOWED) {
        return errorWriter.methodNotAllowed(exchange, match.allowedMethods());
      }
      return errorWriter.routeNotFound(exchange);
    }

    RouteConfig route = match.matchedRoute().orElseThrow();
    exchange.getAttributes().put(GatewayAttributes.MATCHED_ROUTE_ID, route.id());
    return securityPipeline
        .enforce(exchange, bundle, route)
        .then(
            Mono.defer(
                () -> {
                  if (exchange.getResponse().isCommitted()) {
                    return Mono.empty();
                  }
                  URI upstreamUri = bundle.selectUpstream(route);
                  exchange
                      .getAttributes()
                      .put(GatewayAttributes.UPSTREAM_AUTHORITY, upstreamUri.getAuthority());
                  URI targetUri = buildUpstreamUri(upstreamUri, request);
                  return forward(exchange, upstreamUri.getAuthority(), targetUri, requestId);
                }));
  }

  private URI buildUpstreamUri(URI base, ServerHttpRequest request) {
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
      ServerWebExchange exchange, String upstreamHost, URI targetUri, String requestId) {
    ServerHttpRequest incoming = exchange.getRequest();
    String normalizedClientHost = normalizedClientHost(incoming);

    return webClient
        .method(incoming.getMethod())
        .uri(targetUri)
        .headers(
            headers -> {
              headers.addAll(incoming.getHeaders());
              HopByHopHeaders.sanitizeClientRequestHeaders(headers);
              headers.remove(HttpHeaders.HOST);
              headers.set(HttpHeaders.HOST, upstreamHost);
              headers.set(RequestIdSupport.HEADER, requestId);
              headers.set("X-Forwarded-Host", normalizedClientHost);
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
            WebClientRequestException.class, ex -> errorWriter.upstreamUnavailable(exchange, ex))
        .onErrorResume(
            Throwable.class,
            ex -> {
              if (exchange.getResponse().isCommitted()) {
                log.warn(
                    "requestId={} routeId={} upstream={} error after response commit: {}",
                    requestId,
                    exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID),
                    exchange.getAttribute(GatewayAttributes.UPSTREAM_AUTHORITY),
                    ex.getClass().getSimpleName());
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }

  static String normalizedClientHost(ServerHttpRequest request) {
    String host = request.getHeaders().getFirst(HttpHeaders.HOST);
    if (host == null || host.isBlank()) {
      return "localhost";
    }
    return HostNormalizer.normalize(host);
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
        .writeWith(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class))
        .onErrorResume(
            Throwable.class,
            ex -> {
              if (exchange.getResponse().isCommitted()) {
                log.warn("requestId={} upstream response stream failed after commit", requestId);
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }
}
