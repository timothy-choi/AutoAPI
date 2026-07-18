package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.persistence.ServiceInstanceEntity;
import com.autoapi.controlplane.persistence.ServiceInstanceRepositoryCustom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(DiscoveryProperties.class)
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.discovery.enabled"},
    havingValue = "true",
    matchIfMissing = true)
class DiscoveryAutoConfiguration {

  @Configuration
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.discovery.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  static class StaleReaperConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StaleReaperConfiguration.class);

    StaleReaperConfiguration(
        DiscoveryProperties properties,
        ServiceInstanceRepositoryCustom repositoryCustom,
        DiscoveredServiceService discoveredServiceService,
        DiscoveryRuntimePublisher runtimePublisher,
        DiscoveryMetrics metrics,
        Clock clock) {
      Disposable ignored =
          Flux.interval(properties.staleReaperInterval())
              .concatMap(
                  tick ->
                      repositoryCustom
                          .transitionExpiredLeasesToStale(
                              OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)),
                              properties.staleReaperBatchSize())
                          .concatMap(
                              instance ->
                                  handleStaleTransition(
                                      instance,
                                      discoveredServiceService,
                                      runtimePublisher,
                                      metrics))
                          .then())
              .onErrorContinue(
                  (error, obj) ->
                      log.warn("Stale instance reaper iteration failed: {}", error.getMessage()))
              .subscribe();
    }

    private static Mono<Void> handleStaleTransition(
        ServiceInstanceEntity instance,
        DiscoveredServiceService discoveredServiceService,
        DiscoveryRuntimePublisher runtimePublisher,
        DiscoveryMetrics metrics) {
      metrics.recordStaleTransition(instance.serviceId().toString());
      log.info(
          "service_instance_stale serviceId={} instanceId={} reason=lease_expired",
          instance.serviceId(),
          instance.instanceId());
      return discoveredServiceService
          .incrementMembershipVersion(instance.serviceId())
          .then(runtimePublisher.publishAffectedApis(instance.serviceId()));
    }
  }
}
