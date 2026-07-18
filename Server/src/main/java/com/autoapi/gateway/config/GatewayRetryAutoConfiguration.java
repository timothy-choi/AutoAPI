package com.autoapi.gateway.config;

import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.health.FailureClassifier;
import com.autoapi.gateway.retry.GatewayInternalRetryHandler;
import com.autoapi.gateway.retry.GatewayRetryMetrics;
import com.autoapi.gateway.retry.RetryBudgetRegistry;
import com.autoapi.gateway.retry.RetryingProxyExecutor;
import com.autoapi.gateway.retry.UpstreamAttemptExecutor;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import com.autoapi.web.ErrorResponseWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
public class GatewayRetryAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  RetryBudgetRegistry retryBudgetRegistry(Clock gatewayHealthClock) {
    return new RetryBudgetRegistry(gatewayHealthClock);
  }

  @Bean
  @ConditionalOnMissingBean
  UpstreamAttemptExecutor upstreamAttemptExecutor(
      FailureClassifier failureClassifier,
      ObjectProvider<com.autoapi.gateway.health.TargetHealthRegistry> healthRegistryProvider,
      ObjectProvider<com.autoapi.gateway.health.GatewayUpstreamHealthMetrics> healthMetricsProvider,
      ObjectProvider<com.autoapi.gateway.circuitbreaker.CircuitBreakerRegistry>
          circuitBreakerRegistryProvider,
      GatewayProperties gatewayProperties) {
    Duration connectTimeout =
        Duration.ofMillis(gatewayProperties.retry().upstreamConnectTimeoutMs());
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .responseTimeout(Duration.ofSeconds(30));
    WebClient webClient =
        WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    String gatewayId =
        gatewayProperties.gatewayId() == null ? "unknown" : gatewayProperties.gatewayId();
    return new UpstreamAttemptExecutor(
        webClient,
        failureClassifier,
        healthRegistryProvider,
        healthMetricsProvider,
        circuitBreakerRegistryProvider,
        gatewayId);
  }

  @Bean
  @ConditionalOnMissingBean
  RetryingProxyExecutor retryingProxyExecutor(
      UpstreamAttemptExecutor upstreamAttemptExecutor,
      ObjectProvider<com.autoapi.gateway.circuitbreaker.GatewayTargetSelector>
          targetSelectorProvider,
      RetryBudgetRegistry retryBudgetRegistry,
      ObjectProvider<GatewayRetryMetrics> retryMetricsProvider,
      ErrorResponseWriter errorResponseWriter,
      GatewayProperties gatewayProperties) {
    return new RetryingProxyExecutor(
        upstreamAttemptExecutor,
        targetSelectorProvider,
        retryBudgetRegistry,
        retryMetricsProvider,
        errorResponseWriter,
        gatewayProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  GatewayRetryMetrics gatewayRetryMetrics(MeterRegistry meterRegistry) {
    return new GatewayRetryMetrics(meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  GatewayInternalRetryHandler gatewayInternalRetryHandler(
      RetryBudgetRegistry retryBudgetRegistry, GatewayProperties gatewayProperties) {
    return new GatewayInternalRetryHandler(retryBudgetRegistry, gatewayProperties);
  }
}
