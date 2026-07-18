package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.activation.ConfigActivationService;
import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.configversion.ConfigVersionService;
import com.autoapi.controlplane.persistence.DiscoveredServiceRepositoryCustom;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DiscoveryRuntimePublisher {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryRuntimePublisher.class);

  private final DiscoveredServiceRepositoryCustom discoveredServiceRepositoryCustom;
  private final ConfigVersionService configVersionService;
  private final ConfigActivationService configActivationService;
  private final DiscoveryProperties properties;
  private final DiscoveryMetrics metrics;

  public DiscoveryRuntimePublisher(
      DiscoveredServiceRepositoryCustom discoveredServiceRepositoryCustom,
      ConfigVersionService configVersionService,
      ConfigActivationService configActivationService,
      DiscoveryProperties properties,
      DiscoveryMetrics metrics) {
    this.discoveredServiceRepositoryCustom = discoveredServiceRepositoryCustom;
    this.configVersionService = configVersionService;
    this.configActivationService = configActivationService;
    this.properties = properties;
    this.metrics = metrics;
  }

  public Mono<Void> publishAffectedApis(UUID serviceId) {
    return discoveredServiceRepositoryCustom
        .findAffectedApiIds(serviceId)
        .flatMap(this::publishAndMaybeActivate)
        .then();
  }

  private Mono<Void> publishAndMaybeActivate(UUID apiId) {
    return configVersionService
        .publish(apiId, "service discovery membership changed")
        .flatMap(
            published -> {
              metrics.recordSnapshotUpdate();
              log.info(
                  "Published discovery snapshot apiId={} version={}", apiId, published.version());
              if (!properties.autoActivateOnMembershipChange()) {
                return Mono.empty();
              }
              return configActivationService
                  .activate(apiId, published.version(), null)
                  .then()
                  .onErrorResume(
                      ControlPlaneException.class,
                      ex -> {
                        log.warn(
                            "Discovery auto-activation skipped apiId={} reason={}",
                            apiId,
                            ex.getMessage());
                        return Mono.empty();
                      });
            })
        .onErrorResume(
            ControlPlaneException.class,
            ex -> {
              if ("CONFIG_VERSION_ALREADY_EXISTS".equals(ex.code())) {
                return Mono.empty();
              }
              log.warn("Discovery publish failed apiId={} reason={}", apiId, ex.getMessage());
              return Mono.empty();
            })
        .then();
  }
}
