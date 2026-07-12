package com.autoapi.proxy;

import com.autoapi.config.HostNormalizer;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.GatewayUpstreamHealthMetrics;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.ProxyAttemptOutcome;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetHealthState;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.gateway.security.GatewaySecurityEnforcer;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.routing.RouteMatchResult;
import com.autoapi.routing.RouteMatcher;
import com.autoapi.routing.RouteNotFoundReason;
import com.autoapi.web.ErrorResponseWriter;
import java.net.URI;
import java.util.List;
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
  private final HealthAwareTargetSelector targetSelector;
  private final TargetHealthRegistry healthRegistry;
  private final FailureClassifier failureClassifier;
  private final GatewayUpstreamHealthMetrics healthMetrics;

  public ProxyHandler(
      ErrorResponseWriter errorWriter,
      ObjectProvider<GatewaySecurityEnforcer> securityPipeline,
      ObjectProvider<HealthAwareTargetSelector> targetSelector,
      ObjectProvider<TargetHealthRegistry> healthRegistry,
      ObjectProvider<FailureClassifier> failureClassifier,
      ObjectProvider<GatewayUpstreamHealthMetrics> healthMetrics) {
    this.errorWriter = errorWriter;
    this.securityPipeline = securityPipeline.getIfAvailable(() -> NOOP_SECURITY);
    this.targetSelector = targetSelector.getIfAvailable();
    this.healthRegistry = healthRegistry.getIfAvailable();
    this.failureClassifier = failureClassifier.getIfAvailable(FailureClassifier::new);
    this.healthMetrics = healthMetrics.getIfAvailable();
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
                  return selectAndForward(exchange, bundle, route, request, requestId);
                }));
  }

  private Mono<Void> selectAndForward(
      ServerWebExchange exchange,
      ActiveRuntimeBundle bundle,
      RouteConfig route,
      ServerHttpRequest request,
      String requestId) {
    UpstreamConfig upstream = route.upstream();
    List<UpstreamTargetReference> targets = upstream.targets();
    if (targets.isEmpty()) {
      URI upstreamUri = upstream.url();
      if (upstreamUri == null) {
        return errorWriter.noAvailableUpstream(exchange);
      }
      exchange
          .getAttributes()
          .put(GatewayAttributes.UPSTREAM_AUTHORITY, upstreamUri.getAuthority());
      URI targetUri = buildUpstreamUri(upstreamUri, request);
      return forward(exchange, upstreamUri.getAuthority(), targetUri, requestId, null, null, 0);
    }

    PassiveHealthPolicy policy =
        upstream.backendHealth() != null
            ? PassiveHealthPolicy.from(upstream.backendHealth())
            : null;
    if (targetSelector == null) {
      return errorWriter.internalError(
          exchange, new IllegalStateException("Health-aware target selector is unavailable"));
    }

    SelectedTarget selected;
    try {
      selected = targetSelector.select(bundle.apiId(), upstream.poolId(), targets, policy);
    } catch (IllegalArgumentException ex) {
      return errorWriter.noAvailableUpstream(exchange);
    }

    UpstreamTargetReference target = selected.target();
    exchange.getAttributes().put(GatewayAttributes.SELECTED_TARGET_ID, target.targetId());
    exchange.getAttributes().put(GatewayAttributes.UPSTREAM_AUTHORITY, target.url().getAuthority());

    if (selected.forcedSelection() && healthMetrics != null) {
      healthMetrics.recordForcedSelection(
          route.id(), upstream.poolId().toString(), target.targetId().toString());
    }
    if (healthMetrics != null) {
      healthMetrics.recordUpstreamRequest(
          route.id(), upstream.poolId().toString(), target.targetId().toString());
    }

    TargetKey targetKey = new TargetKey(bundle.apiId(), upstream.poolId(), target.targetId());
    URI targetUri = buildUpstreamUri(target.url(), request);
    return forward(
        exchange,
        target.url().getAuthority(),
        targetUri,
        requestId,
        targetKey,
        policy,
        targets.size());
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
      ServerWebExchange exchange,
      String upstreamHost,
      URI targetUri,
      String requestId,
      TargetKey targetKey,
      PassiveHealthPolicy policy,
      int poolTargetCount) {
    ServerHttpRequest incoming = exchange.getRequest();
    String normalizedClientHost = normalizedClientHost(incoming);
    ProxyAttemptOutcome outcome = new ProxyAttemptOutcome();
    String routeId = exchange.getAttribute(GatewayAttributes.MATCHED_ROUTE_ID);

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
        .exchangeToMono(
            response ->
                writeUpstreamResponse(
                    exchange,
                    response,
                    requestId,
                    outcome,
                    targetKey,
                    policy,
                    poolTargetCount,
                    routeId))
        .onErrorResume(
            WebClientRequestException.class,
            ex -> {
              recordTransportFailure(outcome, targetKey, policy, poolTargetCount, routeId, ex);
              return errorWriter.upstreamUnavailable(exchange, ex);
            })
        .onErrorResume(
            Throwable.class,
            ex -> {
              if (exchange.getResponse().isCommitted()) {
                recordTransportFailure(outcome, targetKey, policy, poolTargetCount, routeId, ex);
                log.warn(
                    "requestId={} routeId={} targetId={} upstream={} error after response commit: {}",
                    requestId,
                    routeId,
                    targetKey == null ? null : targetKey.targetId(),
                    exchange.getAttribute(GatewayAttributes.UPSTREAM_AUTHORITY),
                    ex.getClass().getSimpleName());
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }

  private Mono<Void> writeUpstreamResponse(
      ServerWebExchange exchange,
      ClientResponse response,
      String requestId,
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      PassiveHealthPolicy policy,
      int poolTargetCount,
      String routeId) {
    recordSuccess(outcome, targetKey, routeId);
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
                recordTransportFailure(outcome, targetKey, policy, poolTargetCount, routeId, ex);
                log.warn(
                    "requestId={} targetId={} upstream response stream failed after commit",
                    requestId,
                    targetKey == null ? null : targetKey.targetId());
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }

  private void recordSuccess(ProxyAttemptOutcome outcome, TargetKey targetKey, String routeId) {
    if (targetKey == null || healthRegistry == null) {
      return;
    }
    outcome.recordSuccess(
        () -> {
          TargetHealthState before = healthRegistry.getState(targetKey);
          boolean wasEjected = before.ejectedUntil() != null;
          healthRegistry.recordSuccess(targetKey);
          if (wasEjected && healthMetrics != null && routeId != null) {
            healthMetrics.recordRecovery(
                routeId, targetKey.poolId().toString(), targetKey.targetId().toString());
          }
        });
  }

  private void recordTransportFailure(
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      PassiveHealthPolicy policy,
      int poolTargetCount,
      String routeId,
      Throwable error) {
    if (targetKey == null || healthRegistry == null) {
      return;
    }
    outcome.recordFailure(
        () ->
            failureClassifier
                .classifyTransportFailure(error)
                .ifPresent(
                    category -> {
                      TargetHealthState before = healthRegistry.getState(targetKey);
                      healthRegistry.recordFailure(targetKey, category, policy, poolTargetCount);
                      TargetHealthState after = healthRegistry.getState(targetKey);
                      if (routeId != null && healthMetrics != null) {
                        healthMetrics.recordTransportFailure(
                            routeId,
                            targetKey.poolId().toString(),
                            targetKey.targetId().toString(),
                            category.name());
                      }
                      if (after.ejectedUntil() != null
                          && (before.ejectedUntil() == null
                              || !after.ejectedUntil().equals(before.ejectedUntil()))) {
                        if (healthMetrics != null && routeId != null) {
                          healthMetrics.recordEjection(
                              routeId,
                              targetKey.poolId().toString(),
                              targetKey.targetId().toString(),
                              category.name());
                        }
                      }
                    }));
  }

  static String normalizedClientHost(ServerHttpRequest request) {
    String host = request.getHeaders().getFirst(HttpHeaders.HOST);
    if (host == null || host.isBlank()) {
      return "localhost";
    }
    return HostNormalizer.normalize(host);
  }
}
