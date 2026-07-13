package com.autoapi.gateway.retry;

import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeRetryPolicyConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.SelectedTarget;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.proxy.HopByHopHeaders;
import com.autoapi.web.ErrorResponseWriter;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Bounded, idempotency-aware upstream retry orchestration for a single downstream request. */
public class RetryingProxyExecutor {

  private static final Logger log = LoggerFactory.getLogger(RetryingProxyExecutor.class);

  private final UpstreamAttemptExecutor attemptExecutor;
  private final ObjectProvider<HealthAwareTargetSelector> targetSelectorProvider;
  private final RetryBudgetRegistry retryBudgetRegistry;
  private final ObjectProvider<GatewayRetryMetrics> retryMetricsProvider;
  private final ErrorResponseWriter errorWriter;
  private final long maxReplayBodyBytes;

  public RetryingProxyExecutor(
      UpstreamAttemptExecutor attemptExecutor,
      ObjectProvider<HealthAwareTargetSelector> targetSelectorProvider,
      RetryBudgetRegistry retryBudgetRegistry,
      ObjectProvider<GatewayRetryMetrics> retryMetricsProvider,
      ErrorResponseWriter errorWriter,
      GatewayProperties gatewayProperties) {
    this.attemptExecutor = attemptExecutor;
    this.targetSelectorProvider = targetSelectorProvider;
    this.retryBudgetRegistry = retryBudgetRegistry;
    this.retryMetricsProvider = retryMetricsProvider;
    this.errorWriter = errorWriter;
    this.maxReplayBodyBytes = gatewayProperties.retry().maxReplayBodyBytes();
  }

  public Mono<Void> executeWithRetries(
      ServerWebExchange exchange,
      ActiveRuntimeBundle bundle,
      RouteConfig route,
      ServerHttpRequest request,
      String requestId,
      UpstreamConfig upstream,
      java.util.List<UpstreamTargetReference> targets) {
    RuntimeRetryPolicyConfig retryPolicy = route.retry();
    PassiveHealthPolicy healthPolicy =
        upstream.backendHealth() != null
            ? PassiveHealthPolicy.from(upstream.backendHealth())
            : null;

    if (retryPolicy == null || !retryPolicy.retriesEnabled()) {
      return executeSingleAttemptWithoutCapture(
          exchange, bundle, route, request, requestId, upstream, targets, healthPolicy, 1);
    }

    boolean idempotencyPresent =
        IdempotencyKeyValidator.isPresent(
            request.getHeaders().getFirst(IdempotencyKeyValidator.HEADER));
    boolean idempotencyValid =
        IdempotencyKeyValidator.isValid(
            request.getHeaders().getFirst(IdempotencyKeyValidator.HEADER));

    return ReplayableRequest.capture(request, maxReplayBodyBytes)
        .flatMap(
            replayable -> {
              if (replayable.state() == ReplayableRequest.BodyState.TOO_LARGE) {
                recordBodyUnreplayable(route, retryPolicy);
                log.debug(
                    "requestId={} routeId={} retry disabled because request body exceeds replay limit",
                    requestId,
                    route.id());
                return executeSingleAttemptWithoutCapture(
                    exchange,
                    bundle,
                    route,
                    request,
                    requestId,
                    upstream,
                    targets,
                    healthPolicy,
                    1);
              }
              RetryBudgetKey budgetKey =
                  new RetryBudgetKey(bundle.apiId(), route.id(), retryPolicy.policyId());
              retryBudgetRegistry.recordOriginalRequest(budgetKey, retryPolicy);
              Set<UUID> attempted = RetryTargetSelector.newAttemptedSet();
              return attemptLoop(
                  exchange,
                  bundle,
                  route,
                  request,
                  requestId,
                  upstream,
                  targets,
                  healthPolicy,
                  retryPolicy,
                  budgetKey,
                  replayable,
                  attempted,
                  idempotencyPresent,
                  idempotencyValid,
                  1,
                  null);
            });
  }

  private Mono<Void> executeSingleAttemptWithoutCapture(
      ServerWebExchange exchange,
      ActiveRuntimeBundle bundle,
      RouteConfig route,
      ServerHttpRequest request,
      String requestId,
      UpstreamConfig upstream,
      java.util.List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      int attemptNumber) {
    return ReplayableRequest.capture(request, maxReplayBodyBytes)
        .flatMap(
            replayable ->
                executeAttempt(
                    exchange,
                    bundle,
                    route,
                    request,
                    requestId,
                    upstream,
                    targets,
                    healthPolicy,
                    route.retry(),
                    null,
                    replayable,
                    RetryTargetSelector.newAttemptedSet(),
                    attemptNumber))
        .flatMap(
            result -> {
              if (result instanceof UpstreamAttemptExecutor.AttemptResult.Success success) {
                return writeSuccess(exchange, success.response(), requestId);
              }
              if (result instanceof UpstreamAttemptExecutor.AttemptResult.Cancelled) {
                return Mono.empty();
              }
              UpstreamAttemptExecutor.AttemptResult.Failure failure =
                  (UpstreamAttemptExecutor.AttemptResult.Failure) result;
              return writeTerminalFailure(exchange, failure);
            });
  }

  private Mono<UpstreamAttemptExecutor.AttemptResult> executeAttempt(
      ServerWebExchange exchange,
      ActiveRuntimeBundle bundle,
      RouteConfig route,
      ServerHttpRequest request,
      String requestId,
      UpstreamConfig upstream,
      java.util.List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeRetryPolicyConfig retryPolicy,
      RetryBudgetKey budgetKey,
      ReplayableRequest replayable,
      Set<UUID> attempted,
      int attemptNumber) {
    HealthAwareTargetSelector selector = targetSelectorProvider.getIfAvailable();
    if (selector == null) {
      return Mono.error(new IllegalStateException("Health-aware target selector is unavailable"));
    }
    SelectedTarget selected =
        RetryTargetSelector.selectForAttempt(
            selector,
            bundle.apiId(),
            upstream.poolId(),
            targets,
            healthPolicy,
            attempted,
            attemptNumber);
    UpstreamTargetReference target = selected.target();
    attempted.add(target.targetId());

    TargetKey targetKey = new TargetKey(bundle.apiId(), upstream.poolId(), target.targetId());
    URI targetUri = buildUpstreamUri(target.url(), request);
    Duration timeout =
        retryPolicy == null
            ? Duration.ofSeconds(30)
            : Duration.ofMillis(retryPolicy.perAttemptTimeoutMs());

    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null && retryPolicy != null) {
      metrics.recordAttempt(route.id(), retryPolicy.policyId().toString());
    }

    return attemptExecutor.execute(
        exchange,
        request,
        targetUri,
        target.url().getAuthority(),
        requestId,
        targetKey,
        healthPolicy,
        targets.size(),
        route.id(),
        replayable,
        timeout);
  }

  private Mono<Void> attemptLoop(
      ServerWebExchange exchange,
      ActiveRuntimeBundle bundle,
      RouteConfig route,
      ServerHttpRequest request,
      String requestId,
      UpstreamConfig upstream,
      java.util.List<UpstreamTargetReference> targets,
      PassiveHealthPolicy healthPolicy,
      RuntimeRetryPolicyConfig retryPolicy,
      RetryBudgetKey budgetKey,
      ReplayableRequest replayable,
      Set<UUID> attempted,
      boolean idempotencyPresent,
      boolean idempotencyValid,
      int attemptNumber,
      UpstreamAttemptExecutor.AttemptResult.Failure lastFailure) {

    if (exchange.getResponse().isCommitted()) {
      return Mono.empty();
    }

    return executeAttempt(
            exchange,
            bundle,
            route,
            request,
            requestId,
            upstream,
            targets,
            healthPolicy,
            retryPolicy,
            budgetKey,
            replayable,
            attempted,
            attemptNumber)
        .flatMap(
            result -> {
              if (result instanceof UpstreamAttemptExecutor.AttemptResult.Success success) {
                if (attemptNumber > 1) {
                  recordRetrySucceeded(route, retryPolicy);
                }
                exchange.getAttributes().put(GatewayAttributes.UPSTREAM_ATTEMPTS, attemptNumber);
                return writeSuccess(exchange, success.response(), requestId);
              }
              if (result instanceof UpstreamAttemptExecutor.AttemptResult.Cancelled) {
                return Mono.empty();
              }
              UpstreamAttemptExecutor.AttemptResult.Failure failure =
                  (UpstreamAttemptExecutor.AttemptResult.Failure) result;
              if (attemptNumber >= retryPolicy.maxAttempts()) {
                recordRetryExhausted(route, retryPolicy, failure);
                exchange.getAttributes().put(GatewayAttributes.UPSTREAM_ATTEMPTS, attemptNumber);
                return writeTerminalFailure(exchange, failure);
              }
              if (!RetryEligibilityEvaluator.isMethodRetryable(
                  retryPolicy, request.getMethod(), idempotencyValid)) {
                recordMethodDenied(route, retryPolicy, idempotencyPresent, idempotencyValid);
                exchange.getAttributes().put(GatewayAttributes.UPSTREAM_ATTEMPTS, attemptNumber);
                return writeTerminalFailure(exchange, failure);
              }
              if (failure.category() == null
                  || !RetryEligibilityEvaluator.isFailureRetryable(
                      retryPolicy, failure.category())) {
                exchange.getAttributes().put(GatewayAttributes.UPSTREAM_ATTEMPTS, attemptNumber);
                return writeTerminalFailure(exchange, failure);
              }
              if (!retryBudgetRegistry.tryConsumeRetry(budgetKey, retryPolicy)) {
                recordBudgetDenied(route, retryPolicy, failure);
                exchange.getAttributes().put(GatewayAttributes.UPSTREAM_ATTEMPTS, attemptNumber);
                return writeTerminalFailure(exchange, failure);
              }
              recordRetryAttempted(route, retryPolicy, failure);
              log.debug(
                  "requestId={} routeId={} attempt={} retry permitted category={}",
                  requestId,
                  route.id(),
                  attemptNumber,
                  failure.category());
              return attemptLoop(
                  exchange,
                  bundle,
                  route,
                  request,
                  requestId,
                  upstream,
                  targets,
                  healthPolicy,
                  retryPolicy,
                  budgetKey,
                  replayable,
                  attempted,
                  idempotencyPresent,
                  idempotencyValid,
                  attemptNumber + 1,
                  failure);
            });
  }

  private Mono<Void> writeSuccess(
      ServerWebExchange exchange, ClientResponse response, String requestId) {
    exchange.getResponse().setStatusCode(response.statusCode());
    org.springframework.http.HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
    responseHeaders.addAll(response.headers().asHttpHeaders());
    HopByHopHeaders.sanitizeUpstreamResponseHeaders(responseHeaders);
    responseHeaders.set(com.autoapi.middleware.RequestIdSupport.HEADER, requestId);
    return exchange
        .getResponse()
        .writeWith(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
  }

  private Mono<Void> writeTerminalFailure(
      ServerWebExchange exchange, UpstreamAttemptExecutor.AttemptResult.Failure failure) {
    if (failure.responseTimeout()) {
      return errorWriter.upstreamTimeout(exchange, failure.error());
    }
    if (failure.error()
        instanceof
        org.springframework.web.reactive.function.client.WebClientRequestException webEx) {
      return errorWriter.upstreamUnavailable(exchange, webEx);
    }
    return errorWriter.upstreamUnavailable(exchange, failure.error());
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

  private void recordRetryAttempted(
      RouteConfig route,
      RuntimeRetryPolicyConfig policy,
      UpstreamAttemptExecutor.AttemptResult.Failure failure) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordRetryAttempted(
          route.id(),
          policy.policyId().toString(),
          failure.category() == null ? "unknown" : failure.category().name());
    }
  }

  private void recordRetrySucceeded(RouteConfig route, RuntimeRetryPolicyConfig policy) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordRetrySucceeded(route.id(), policy.policyId().toString());
    }
  }

  private void recordRetryExhausted(
      RouteConfig route,
      RuntimeRetryPolicyConfig policy,
      UpstreamAttemptExecutor.AttemptResult.Failure failure) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordRetryExhausted(
          route.id(),
          policy.policyId().toString(),
          failure.category() == null ? "unknown" : failure.category().name());
    }
  }

  private void recordBudgetDenied(
      RouteConfig route,
      RuntimeRetryPolicyConfig policy,
      UpstreamAttemptExecutor.AttemptResult.Failure failure) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordBudgetDenied(route.id(), policy.policyId().toString());
    }
    log.debug(
        "routeId={} retry denied because budget exhausted category={}",
        route.id(),
        failure.category());
  }

  private void recordMethodDenied(
      RouteConfig route,
      RuntimeRetryPolicyConfig policy,
      boolean idempotencyPresent,
      boolean idempotencyValid) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordMethodDenied(route.id(), policy.policyId().toString());
    }
    log.debug(
        "routeId={} retry denied methodNotRetryable idempotencyKeyPresent={} idempotencyKeyValid={}",
        route.id(),
        idempotencyPresent,
        idempotencyValid);
  }

  private void recordBodyUnreplayable(RouteConfig route, RuntimeRetryPolicyConfig policy) {
    GatewayRetryMetrics metrics = retryMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordBodyUnreplayable(route.id(), policy.policyId().toString());
    }
  }
}
