package com.autoapi.proxy;

import com.autoapi.config.HostNormalizer;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.GatewayUpstreamHealthMetrics;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.ProxyAttemptOutcome;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetHealthState;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.gateway.retry.RetryingProxyExecutor;
import com.autoapi.gateway.security.GatewaySecurityEnforcer;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.routing.RouteMatchResult;
import com.autoapi.routing.RouteMatcher;
import com.autoapi.routing.RouteNotFoundReason;
import com.autoapi.web.ErrorResponseWriter;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private final ObjectProvider<HealthAwareTargetSelector> targetSelectorProvider;
  private final ObjectProvider<TargetHealthRegistry> healthRegistryProvider;
  private final FailureClassifier failureClassifier;
  private final ObjectProvider<GatewayUpstreamHealthMetrics> healthMetricsProvider;
  private final ObjectProvider<RetryingProxyExecutor> retryingProxyExecutorProvider;
  private final String gatewayId;

  public ProxyHandler(
      ErrorResponseWriter errorWriter,
      ObjectProvider<GatewaySecurityEnforcer> securityPipeline,
      ObjectProvider<HealthAwareTargetSelector> targetSelector,
      ObjectProvider<TargetHealthRegistry> healthRegistry,
      ObjectProvider<FailureClassifier> failureClassifier,
      ObjectProvider<GatewayUpstreamHealthMetrics> healthMetrics,
      ObjectProvider<GatewayProperties> gatewayProperties,
      ObjectProvider<RetryingProxyExecutor> retryingProxyExecutor) {
    this.errorWriter = errorWriter;
    this.securityPipeline = securityPipeline.getIfAvailable(() -> NOOP_SECURITY);
    this.targetSelectorProvider = targetSelector;
    this.healthRegistryProvider = healthRegistry;
    this.failureClassifier = failureClassifier.getIfAvailable(FailureClassifier::new);
    this.healthMetricsProvider = healthMetrics;
    this.retryingProxyExecutorProvider = retryingProxyExecutor;
    GatewayProperties properties = gatewayProperties.getIfAvailable();
    this.gatewayId =
        properties == null || properties.gatewayId() == null ? "unknown" : properties.gatewayId();
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
    if (targetSelectorProvider.getIfAvailable() == null) {
      return errorWriter.internalError(
          exchange, new IllegalStateException("Health-aware target selector is unavailable"));
    }

    RetryingProxyExecutor retryExecutor = retryingProxyExecutorProvider.getIfAvailable();
    if (retryExecutor != null) {
      return retryExecutor.executeWithRetries(
          exchange, bundle, route, request, requestId, upstream, targets);
    }

    SelectedTarget selected;
    try {
      selected =
          targetSelectorProvider
              .getObject()
              .select(bundle.apiId(), upstream.poolId(), targets, policy);
    } catch (IllegalArgumentException ex) {
      return errorWriter.noAvailableUpstream(exchange);
    }

    UpstreamTargetReference target = selected.target();
    exchange.getAttributes().put(GatewayAttributes.SELECTED_TARGET_ID, target.targetId());
    exchange.getAttributes().put(GatewayAttributes.UPSTREAM_AUTHORITY, target.url().getAuthority());

    if (selected.forcedSelection() && healthMetricsProvider.getIfAvailable() != null) {
      healthMetricsProvider
          .getObject()
          .recordForcedSelection(
              route.id(), upstream.poolId().toString(), target.targetId().toString());
    }
    GatewayUpstreamHealthMetrics metrics = healthMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordUpstreamRequest(
          route.id(), upstream.poolId().toString(), target.targetId().toString());
    }

    TargetKey targetKey = new TargetKey(bundle.apiId(), upstream.poolId(), target.targetId());
    assertSelectedTargetKey(exchange, targetKey);
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
            ex ->
                handleUpstreamTransportFailure(
                    exchange, requestId, outcome, targetKey, policy, poolTargetCount, routeId, ex))
        .onErrorResume(
            Throwable.class,
            ex -> {
              if (exchange.getResponse().isCommitted()) {
                recordTransportFailure(
                    exchange, requestId, outcome, targetKey, policy, poolTargetCount, routeId, ex);
                log.warn(
                    "requestId={} routeId={} targetId={} upstream={} error after response commit: {}",
                    requestId,
                    routeId,
                    targetKey == null ? null : targetKey.targetId(),
                    exchange.getAttribute(GatewayAttributes.UPSTREAM_AUTHORITY),
                    ex.getClass().getSimpleName());
                if (log.isDebugEnabled()) {
                  log.debug("requestId={} upstream response stream failure detail", requestId, ex);
                }
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }

  private Mono<Void> handleUpstreamTransportFailure(
      ServerWebExchange exchange,
      String requestId,
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      PassiveHealthPolicy policy,
      int poolTargetCount,
      String routeId,
      WebClientRequestException error) {
    recordTransportFailure(
        exchange, requestId, outcome, targetKey, policy, poolTargetCount, routeId, error);
    return errorWriter.upstreamUnavailable(exchange, error);
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
    recordSuccess(exchange, requestId, outcome, targetKey, routeId);
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
                recordTransportFailure(
                    exchange, requestId, outcome, targetKey, policy, poolTargetCount, routeId, ex);
                log.warn(
                    "requestId={} targetId={} upstream response stream failed after commit",
                    requestId,
                    targetKey == null ? null : targetKey.targetId());
                if (log.isDebugEnabled()) {
                  log.debug("requestId={} upstream response stream failure detail", requestId, ex);
                }
                return Mono.error(ex);
              }
              return errorWriter.internalError(exchange, ex);
            });
  }

  private void recordSuccess(
      ServerWebExchange exchange,
      String requestId,
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      String routeId) {
    TargetHealthRegistry registry = healthRegistryProvider.getIfAvailable();
    if (targetKey == null || registry == null) {
      return;
    }
    assertSelectedTargetKey(exchange, targetKey);
    GatewayUpstreamHealthMetrics metrics = healthMetricsProvider.getIfAvailable();
    outcome.recordSuccess(
        () -> {
          TargetHealthState before = registry.getState(targetKey);
          boolean wasEjected = before.ejectedUntil() != null;
          registry.recordSuccess(targetKey);
          TargetHealthState after = registry.getState(targetKey);
          log.debug(
              "Passive health recorded transport success requestId={} gatewayId={} routeId={} apiId={} poolId={} targetId={} consecutiveFailures={}",
              requestId,
              gatewayId,
              routeId,
              targetKey.apiId(),
              targetKey.poolId(),
              targetKey.targetId(),
              after.consecutiveQualifyingFailures());
          if (wasEjected && metrics != null && routeId != null) {
            metrics.recordRecovery(
                routeId, targetKey.poolId().toString(), targetKey.targetId().toString());
          }
        });
  }

  private void recordTransportFailure(
      ServerWebExchange exchange,
      String requestId,
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      PassiveHealthPolicy policy,
      int poolTargetCount,
      String routeId,
      Throwable error) {
    TargetHealthRegistry registry = healthRegistryProvider.getIfAvailable();
    if (targetKey == null || registry == null) {
      return;
    }
    assertSelectedTargetKey(exchange, targetKey);
    Optional<FailureCategory> category = failureClassifier.resolveQualifyingCategory(error);
    if (category.isEmpty()) {
      log.debug(
          "requestId={} targetId={} ignored non-qualifying transport error type={}",
          requestId,
          targetKey.targetId(),
          error.getClass().getSimpleName());
      return;
    }
    FailureCategory resolvedCategory = category.get();
    GatewayUpstreamHealthMetrics metrics = healthMetricsProvider.getIfAvailable();
    outcome.recordFailure(
        () -> {
          TargetHealthState before = registry.getState(targetKey);
          registry.recordFailure(targetKey, resolvedCategory, policy, poolTargetCount);
          TargetHealthState after = registry.getState(targetKey);
          boolean ejectedNow =
              after.ejectedUntil() != null
                  && (before.ejectedUntil() == null
                      || !after.ejectedUntil().equals(before.ejectedUntil()));
          log.warn(
              "Passive health recorded transport failure requestId={} gatewayId={} routeId={} apiId={} poolId={} targetId={} category={} consecutiveFailures={} ejected={}",
              requestId,
              gatewayId,
              routeId,
              targetKey.apiId(),
              targetKey.poolId(),
              targetKey.targetId(),
              resolvedCategory,
              after.consecutiveQualifyingFailures(),
              ejectedNow);
          if (log.isDebugEnabled()) {
            log.debug("requestId={} upstream transport failure detail", requestId, error);
          }
          if (routeId != null && metrics != null) {
            metrics.recordTransportFailure(
                routeId,
                targetKey.poolId().toString(),
                targetKey.targetId().toString(),
                resolvedCategory.name());
          }
          if (ejectedNow && metrics != null && routeId != null) {
            metrics.recordEjection(
                routeId,
                targetKey.poolId().toString(),
                targetKey.targetId().toString(),
                resolvedCategory.name());
          }
        });
  }

  private static void assertSelectedTargetKey(ServerWebExchange exchange, TargetKey targetKey) {
    UUID selectedTargetId = exchange.getAttribute(GatewayAttributes.SELECTED_TARGET_ID);
    if (selectedTargetId != null && !selectedTargetId.equals(targetKey.targetId())) {
      throw new IllegalStateException(
          "Selected target id "
              + selectedTargetId
              + " does not match passive-health key "
              + targetKey.targetId());
    }
  }

  public static String normalizedClientHost(ServerHttpRequest request) {
    String host = request.getHeaders().getFirst(HttpHeaders.HOST);
    if (host == null || host.isBlank()) {
      return "localhost";
    }
    return HostNormalizer.normalize(host);
  }
}
