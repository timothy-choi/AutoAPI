package com.autoapi.gateway.retry;

import com.autoapi.gateway.health.FailureCategory;
import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.health.GatewayUpstreamHealthMetrics;
import com.autoapi.gateway.health.PassiveHealthPolicy;
import com.autoapi.gateway.health.ProxyAttemptOutcome;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.health.TargetHealthState;
import com.autoapi.gateway.health.TargetKey;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.proxy.GatewayAttributes;
import com.autoapi.proxy.HopByHopHeaders;
import com.autoapi.proxy.ProxyHandler;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Executes one upstream proxy attempt and records passive-health outcome exactly once. */
public final class UpstreamAttemptExecutor {

  private static final Logger log = LoggerFactory.getLogger(UpstreamAttemptExecutor.class);

  private final WebClient webClient;
  private final FailureClassifier failureClassifier;
  private final ObjectProvider<TargetHealthRegistry> healthRegistryProvider;
  private final ObjectProvider<GatewayUpstreamHealthMetrics> healthMetricsProvider;
  private final String gatewayId;

  public UpstreamAttemptExecutor(
      WebClient webClient,
      FailureClassifier failureClassifier,
      ObjectProvider<TargetHealthRegistry> healthRegistryProvider,
      ObjectProvider<GatewayUpstreamHealthMetrics> healthMetricsProvider,
      String gatewayId) {
    this.webClient = webClient;
    this.failureClassifier = failureClassifier;
    this.healthRegistryProvider = healthRegistryProvider;
    this.healthMetricsProvider = healthMetricsProvider;
    this.gatewayId = gatewayId;
  }

  public Mono<AttemptResult> execute(
      ServerWebExchange exchange,
      ServerHttpRequest incoming,
      URI targetUri,
      String upstreamHost,
      String requestId,
      TargetKey targetKey,
      PassiveHealthPolicy healthPolicy,
      int poolTargetCount,
      String routeId,
      ReplayableRequest replayableRequest,
      Duration attemptTimeout) {
    ProxyAttemptOutcome outcome = new ProxyAttemptOutcome();
    exchange.getAttributes().put(GatewayAttributes.SELECTED_TARGET_ID, targetKey.targetId());
    exchange.getAttributes().put(GatewayAttributes.UPSTREAM_AUTHORITY, upstreamHost);

    GatewayUpstreamHealthMetrics metrics = healthMetricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordUpstreamRequest(
          routeId, targetKey.poolId().toString(), targetKey.targetId().toString());
    }

    String normalizedClientHost = ProxyHandler.normalizedClientHost(incoming);
    HttpMethod method = incoming.getMethod();
    Flux<DataBuffer> body =
        replayableRequest == null
            ? incoming.getBody()
            : replayableRequest.toBodyFlux(exchange.getResponse().bufferFactory());

    return webClient
        .method(method)
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
        .body(BodyInserters.fromDataBuffers(body))
        .exchangeToMono(
            response -> {
              recordSuccess(outcome, targetKey, routeId, requestId, healthPolicy, poolTargetCount);
              return Mono.<AttemptResult>just(AttemptResult.success(response, targetKey));
            })
        .timeout(attemptTimeout)
        .onErrorResume(
            error -> {
              if (RetryFailureMapper.isClientCancellation(error)) {
                return Mono.<AttemptResult>just(AttemptResult.cancelled(targetKey));
              }
              Optional<FailureCategory> healthCategory =
                  failureClassifier.resolveQualifyingCategory(error);
              healthCategory.ifPresent(
                  category ->
                      recordFailure(
                          outcome,
                          targetKey,
                          routeId,
                          requestId,
                          healthPolicy,
                          poolTargetCount,
                          category,
                          error));
              Optional<RetryFailureCategory> retryCategory =
                  healthCategory.flatMap(RetryFailureMapper::toRetryCategory);
              if (retryCategory.isEmpty() && RetryFailureMapper.isResponseTimeout(error)) {
                retryCategory = Optional.of(RetryFailureCategory.RESPONSE_TIMEOUT);
              }
              boolean responseTimeout =
                  retryCategory.orElse(null) == RetryFailureCategory.RESPONSE_TIMEOUT;
              return Mono.<AttemptResult>just(
                  AttemptResult.failure(
                      error, retryCategory.orElse(null), responseTimeout, targetKey));
            });
  }

  private void recordSuccess(
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      String routeId,
      String requestId,
      PassiveHealthPolicy healthPolicy,
      int poolTargetCount) {
    TargetHealthRegistry registry = healthRegistryProvider.getIfAvailable();
    if (registry == null) {
      return;
    }
    outcome.recordSuccess(
        () -> {
          registry.recordSuccess(targetKey);
          log.debug(
              "Passive health recorded transport success requestId={} gatewayId={} routeId={} targetId={}",
              requestId,
              gatewayId,
              routeId,
              targetKey.targetId());
        });
  }

  private void recordFailure(
      ProxyAttemptOutcome outcome,
      TargetKey targetKey,
      String routeId,
      String requestId,
      PassiveHealthPolicy healthPolicy,
      int poolTargetCount,
      FailureCategory category,
      Throwable error) {
    TargetHealthRegistry registry = healthRegistryProvider.getIfAvailable();
    if (registry == null) {
      return;
    }
    GatewayUpstreamHealthMetrics metrics = healthMetricsProvider.getIfAvailable();
    outcome.recordFailure(
        () -> {
          TargetHealthState before = registry.getState(targetKey);
          registry.recordFailure(targetKey, category, healthPolicy, poolTargetCount);
          TargetHealthState after = registry.getState(targetKey);
          log.warn(
              "Passive health recorded transport failure requestId={} gatewayId={} routeId={} targetId={} category={} consecutiveFailures={}",
              requestId,
              gatewayId,
              routeId,
              targetKey.targetId(),
              category,
              after.consecutiveQualifyingFailures());
          if (metrics != null) {
            metrics.recordTransportFailure(
                routeId,
                targetKey.poolId().toString(),
                targetKey.targetId().toString(),
                category.name());
          }
        });
  }

  public sealed interface AttemptResult {

    static Success success(ClientResponse response, TargetKey targetKey) {
      return new Success(response, targetKey);
    }

    static Failure failure(
        Throwable error,
        RetryFailureCategory category,
        boolean responseTimeout,
        TargetKey targetKey) {
      return new Failure(error, category, responseTimeout, targetKey);
    }

    static Cancelled cancelled(TargetKey targetKey) {
      return new Cancelled(targetKey);
    }

    TargetKey targetKey();

    record Success(ClientResponse response, TargetKey targetKey) implements AttemptResult {}

    record Failure(
        Throwable error,
        RetryFailureCategory category,
        boolean responseTimeout,
        TargetKey targetKey)
        implements AttemptResult {}

    record Cancelled(TargetKey targetKey) implements AttemptResult {}
  }
}
