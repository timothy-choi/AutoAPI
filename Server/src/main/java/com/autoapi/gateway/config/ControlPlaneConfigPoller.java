package com.autoapi.gateway.config;

import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.config.remote.ConfigStatusReporter;
import com.autoapi.gateway.config.remote.ControlPlaneConfigClient;
import com.autoapi.gateway.config.remote.ControlPlaneConfigClient.DesiredMetadataResponse;
import com.autoapi.gateway.config.remote.ControlPlaneConfigClientException;
import com.autoapi.gateway.config.remote.GatewayRegistrationManager;
import com.autoapi.gateway.config.remote.GatewayRegistrationState;
import com.autoapi.gateway.config.remote.RolloutAssignmentContextHolder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.ConditionalOnAutoApiRole;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnAutoApiRole({AutoApiRole.GATEWAY, AutoApiRole.COMBINED})
@ConditionalOnProperty(name = "autoapi.gateway.config-source", havingValue = "control-plane")
public class ControlPlaneConfigPoller {

  private static final Logger log = LoggerFactory.getLogger(ControlPlaneConfigPoller.class);

  private final ControlPlaneConfigClient client;
  private final LocalGatewayConfigActivator activator;
  private final ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  private final GatewayProperties gatewayProperties;
  private final GatewayRegistrationState registrationState;
  private final GatewayRegistrationManager registrationManager;
  private final ConfigStatusReporter configStatusReporter;
  private final RolloutAssignmentContextHolder rolloutAssignmentContextHolder;
  private final AtomicBoolean pollInProgress = new AtomicBoolean(false);
  private final AtomicBoolean lastFailureLogged = new AtomicBoolean(false);
  private Disposable pollingSubscription;

  public ControlPlaneConfigPoller(
      ControlPlaneConfigClient client,
      LocalGatewayConfigActivator activator,
      ActiveRuntimeConfigHolder activeRuntimeConfigHolder,
      GatewayProperties gatewayProperties,
      GatewayRegistrationState registrationState,
      GatewayRegistrationManager registrationManager,
      ConfigStatusReporter configStatusReporter,
      RolloutAssignmentContextHolder rolloutAssignmentContextHolder) {
    this.client = client;
    this.activator = activator;
    this.activeRuntimeConfigHolder = activeRuntimeConfigHolder;
    this.gatewayProperties = gatewayProperties;
    this.registrationState = registrationState;
    this.registrationManager = registrationManager;
    this.configStatusReporter = configStatusReporter;
    this.rolloutAssignmentContextHolder = rolloutAssignmentContextHolder;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startPolling() {
    validateStartupConfiguration();
    Duration interval = gatewayProperties.pollInterval();
    pollingSubscription =
        Mono.defer(this::serializedPoll)
            .repeatWhen(completed -> completed.delayElements(interval))
            .subscribe(
                null,
                ex ->
                    log.error(
                        "Control-plane config polling terminated apiId={} message={}",
                        gatewayProperties.apiId(),
                        safeMessage(ex.getMessage()),
                        ex));
  }

  @PreDestroy
  public void stopPolling() {
    if (pollingSubscription != null) {
      pollingSubscription.dispose();
    }
  }

  private void validateStartupConfiguration() {
    if (gatewayProperties.apiId() == null) {
      throw new IllegalStateException(
          "autoapi.gateway.api-id is required in control-plane config source mode");
    }
    if (gatewayProperties.controlPlaneBaseUrl() == null
        || gatewayProperties.controlPlaneBaseUrl().isBlank()) {
      throw new IllegalStateException("autoapi.gateway.control-plane-base-url must be configured");
    }
    if (gatewayProperties.pollInterval().isZero()
        || gatewayProperties.pollInterval().isNegative()) {
      throw new IllegalStateException("autoapi.gateway.poll-interval must be positive");
    }
    String gatewayId = gatewayProperties.gatewayId();
    if (gatewayId == null || gatewayId.isBlank()) {
      throw new IllegalStateException(
          "autoapi.gateway.id is required in control-plane config source mode");
    }
    if (!gatewayId.matches("^[a-zA-Z0-9._-]{1,128}$")) {
      throw new IllegalStateException(
          "autoapi.gateway.id must match [a-zA-Z0-9._-] and be at most 128 characters");
    }
  }

  private Mono<Void> serializedPoll() {
    if (!pollInProgress.compareAndSet(false, true)) {
      return Mono.empty();
    }
    return performPoll().doFinally(signal -> pollInProgress.set(false));
  }

  private Mono<Void> performPoll() {
    if (!registrationState.isRegistered()) {
      return registrationManager.awaitRegistered().then(Mono.defer(this::performPollOnce));
    }
    return performPollOnce();
  }

  private Mono<Void> performPollOnce() {
    String currentHash =
        activeRuntimeConfigHolder.getActive() == null
            ? null
            : activeRuntimeConfigHolder.getActive().contentHash();
    String currentEtag =
        activeRuntimeConfigHolder.getActive() == null ? null : etagForActive(currentHash);
    return client
        .fetchDesiredMetadata(currentEtag == null ? null : "\"" + currentEtag + "\"")
        .flatMap(
            optionalMetadata -> {
              if (optionalMetadata.isEmpty()) {
                log.debug(
                    "Control-plane desired config unchanged apiId={} activeVersion={}",
                    gatewayProperties.apiId(),
                    activeVersion());
                return Mono.empty();
              }
              DesiredMetadataResponse metadata = optionalMetadata.get();
              rolloutAssignmentContextHolder.update(
                  metadata.rolloutId(),
                  metadata.rolloutStageIndex(),
                  metadata.assignmentGeneration());
              return client
                  .fetchSnapshot(metadata.version(), metadata.contentHash())
                  .flatMap(snapshot -> activateAndReport(snapshot, metadata));
            })
        .doOnSuccess(
            ignored -> {
              if (lastFailureLogged.compareAndSet(true, false)) {
                log.info(
                    "Control-plane config polling recovered apiId={} activeVersion={}",
                    gatewayProperties.apiId(),
                    activeVersion());
              }
            })
        .onErrorResume(
            ControlPlaneConfigClientException.class,
            ex -> {
              logPollFailure("control_plane_client", ex.getMessage());
              return Mono.empty();
            })
        .onErrorResume(
            Throwable.class,
            ex -> {
              logPollFailure("unexpected", ex.getMessage());
              return Mono.empty();
            })
        .then();
  }

  private Mono<Void> activateAndReport(
      StoredRuntimeSnapshot snapshot, DesiredMetadataResponse metadata) {
    ActiveRuntimeBundle current = activeRuntimeConfigHolder.getActive();
    if (current != null
        && current.version() == metadata.version()
        && current.contentHash().equals(metadata.contentHash())) {
      return Mono.empty();
    }
    GatewayActivationAttempt attempt = activator.activateCandidate(snapshot);
    configStatusReporter.submit(attempt);
    return Mono.empty();
  }

  private void logPollFailure(String category, String message) {
    if (activeRuntimeConfigHolder.hasActiveConfig()) {
      if (lastFailureLogged.compareAndSet(false, true)) {
        log.warn(
            "Control-plane config poll failed apiId={} activeVersion={} category={} message={}",
            gatewayProperties.apiId(),
            activeVersion(),
            category,
            safeMessage(message));
      } else if (log.isDebugEnabled()) {
        log.debug(
            "Control-plane config poll failed apiId={} category={} message={}",
            gatewayProperties.apiId(),
            category,
            safeMessage(message));
      }
      return;
    }
    log.warn(
        "Control-plane config poll failed before initial activation apiId={} category={} message={}",
        gatewayProperties.apiId(),
        category,
        safeMessage(message));
  }

  private Long activeVersion() {
    ActiveRuntimeBundle active = activeRuntimeConfigHolder.getActive();
    return active == null ? null : active.version();
  }

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "unknown";
    }
    return message.length() > 200 ? message.substring(0, 200) : message;
  }

  private String etagForActive(String contentHash) {
    RolloutAssignmentContextHolder.RolloutAssignmentContext context =
        rolloutAssignmentContextHolder.get();
    if (context != null) {
      return contentHash + ":" + context.rolloutId() + ":" + context.assignmentGeneration();
    }
    return contentHash;
  }
}
