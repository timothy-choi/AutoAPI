package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.persistence.DiscoveredServiceEntity;
import com.autoapi.controlplane.persistence.ServiceInstanceEntity;
import com.autoapi.controlplane.persistence.ServiceInstanceRepositoryCustom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ServiceInstanceService {

  private static final Logger log = LoggerFactory.getLogger(ServiceInstanceService.class);
  public static final String REGISTRATION_TOKEN_HEADER = "X-Service-Registration-Token";

  private final DiscoveredServiceService discoveredServiceService;
  private final ServiceRegistrationCredentialService credentialService;
  private final ServiceInstanceRepositoryCustom repositoryCustom;
  private final DiscoveryRuntimePublisher runtimePublisher;
  private final DiscoveryMetrics metrics;
  private final DiscoveryProperties properties;

  public ServiceInstanceService(
      DiscoveredServiceService discoveredServiceService,
      ServiceRegistrationCredentialService credentialService,
      ServiceInstanceRepositoryCustom repositoryCustom,
      DiscoveryRuntimePublisher runtimePublisher,
      DiscoveryMetrics metrics,
      DiscoveryProperties properties) {
    this.discoveredServiceService = discoveredServiceService;
    this.credentialService = credentialService;
    this.repositoryCustom = repositoryCustom;
    this.runtimePublisher = runtimePublisher;
    this.metrics = metrics;
    this.properties = properties;
  }

  public Mono<ServiceInstanceEntity> register(
      UUID serviceId, ServerRequest request, RegisterInstanceRequest body) {
    return authorize(serviceId, request)
        .then(discoveredServiceService.getById(serviceId))
        .flatMap(
            service -> {
              if (!service.enabled()) {
                return Mono.error(ControlPlaneException.invalidRequest("Service is disabled"));
              }
              return performRegister(service, body);
            });
  }

  public Mono<ServiceInstanceEntity> heartbeat(
      UUID serviceId, String instanceId, ServerRequest request, HeartbeatRequest body) {
    return authorize(serviceId, request)
        .then(discoveredServiceService.getById(serviceId))
        .flatMap(
            service -> {
              if (!service.enabled()) {
                return Mono.error(ControlPlaneException.invalidRequest("Service is disabled"));
              }
              int leaseSeconds =
                  body == null || body.leaseDurationSeconds() == null
                      ? (int) properties.defaultLeaseDuration().getSeconds()
                      : body.leaseDurationSeconds();
              ServiceInstanceValidation.validateLeaseDurationSeconds(leaseSeconds, properties);
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              OffsetDateTime leaseExpiresAt = now.plusSeconds(leaseSeconds);
              return repositoryCustom
                  .findByServiceIdAndInstanceId(serviceId, instanceId)
                  .switchIfEmpty(
                      Mono.error(ControlPlaneException.notFound("Service instance was not found")))
                  .flatMap(
                      before ->
                          repositoryCustom
                              .refreshHeartbeat(serviceId, instanceId, now, leaseExpiresAt)
                              .flatMap(
                                  after -> {
                                    metrics.recordHeartbeat(
                                        service.name(), after.status(), "success");
                                    if ("STALE".equals(before.status())
                                        && "READY".equals(after.status())) {
                                      metrics.recordRecovery(service.name());
                                      return discoveredServiceService
                                          .incrementMembershipVersion(serviceId)
                                          .thenReturn(after)
                                          .flatMap(
                                              entity ->
                                                  runtimePublisher
                                                      .publishAffectedApis(serviceId)
                                                      .thenReturn(entity));
                                    }
                                    return Mono.just(after);
                                  }));
            })
        .onErrorMap(
            ControlPlaneException.class,
            ex -> {
              if ("RESOURCE_NOT_FOUND".equals(ex.code()) || "UNAUTHORIZED".equals(ex.code())) {
                return ex;
              }
              metrics.recordHeartbeatFailure(serviceId.toString(), ex.code());
              return ex;
            });
  }

  public Mono<ServiceInstanceEntity> drain(
      UUID serviceId, String instanceId, ServerRequest request) {
    return authorize(serviceId, request)
        .then(
            repositoryCustom
                .markDraining(serviceId, instanceId, OffsetDateTime.now(ZoneOffset.UTC))
                .switchIfEmpty(
                    Mono.error(ControlPlaneException.notFound("Service instance was not found"))))
        .flatMap(
            updated ->
                discoveredServiceService
                    .incrementMembershipVersion(serviceId)
                    .thenReturn(updated)
                    .flatMap(
                        entity ->
                            runtimePublisher.publishAffectedApis(serviceId).thenReturn(entity)));
  }

  public Mono<ServiceInstanceEntity> deregister(
      UUID serviceId, String instanceId, ServerRequest request) {
    return authorize(serviceId, request)
        .then(
            repositoryCustom.deregister(serviceId, instanceId, OffsetDateTime.now(ZoneOffset.UTC)))
        .flatMap(
            updated ->
                discoveredServiceService
                    .incrementMembershipVersion(serviceId)
                    .thenReturn(updated)
                    .flatMap(
                        entity -> {
                          metrics.recordDeregistration(serviceId.toString());
                          return runtimePublisher.publishAffectedApis(serviceId).thenReturn(entity);
                        }));
  }

  public Flux<ServiceInstanceEntity> list(
      UUID serviceId,
      String status,
      String zone,
      String region,
      String instanceId,
      int limit,
      int offset) {
    return discoveredServiceService
        .getById(serviceId)
        .thenMany(
            repositoryCustom.listFiltered(
                serviceId, status, zone, region, instanceId, limit, offset));
  }

  public Mono<ServiceInstanceEntity> get(UUID serviceId, String instanceId) {
    return discoveredServiceService
        .getById(serviceId)
        .then(
            repositoryCustom
                .findByServiceIdAndInstanceId(serviceId, instanceId)
                .switchIfEmpty(
                    Mono.error(ControlPlaneException.notFound("Service instance was not found"))));
  }

  public Flux<ServiceInstanceEntity> findEligible(UUID serviceId, OffsetDateTime now) {
    return repositoryCustom.findEligibleByServiceId(serviceId, now);
  }

  private Mono<ServiceInstanceEntity> performRegister(
      DiscoveredServiceEntity service, RegisterInstanceRequest body) {
    ServiceInstanceValidation.validateInstanceId(body.instanceId());
    ServiceInstanceValidation.validateHost(body.host());
    ServiceInstanceValidation.validatePort(body.port());
    String scheme =
        ServiceInstanceValidation.normalizeScheme(body.scheme(), service.defaultScheme());
    int weight = body.weight() == null ? 100 : body.weight();
    ServiceInstanceValidation.validateWeight(weight);
    int leaseSeconds =
        body.leaseDurationSeconds() == null
            ? (int) properties.defaultLeaseDuration().getSeconds()
            : body.leaseDurationSeconds();
    ServiceInstanceValidation.validateLeaseDurationSeconds(leaseSeconds, properties);
    ServiceInstanceValidation.validateOptionalLabel(body.zone(), "zone");
    ServiceInstanceValidation.validateOptionalLabel(body.region(), "region");
    String metadataJson =
        body.metadata() == null ? "{}" : ServiceMetadataValidator.normalizeOrEmpty(body.metadata());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime leaseExpiresAt = now.plusSeconds(leaseSeconds);
    return repositoryCustom
        .findByServiceIdAndInstanceId(service.id(), body.instanceId())
        .map(existing -> isRoutingRelevantChange(existing, body, scheme, weight))
        .defaultIfEmpty(true)
        .flatMap(
            routingChanged ->
                repositoryCustom
                    .upsertRegistration(
                        service.id(),
                        body.instanceId(),
                        body.host(),
                        body.port(),
                        scheme,
                        body.zone(),
                        body.region(),
                        weight,
                        now,
                        leaseExpiresAt,
                        metadataJson)
                    .flatMap(
                        saved -> {
                          metrics.recordRegistration(service.name(), "success");
                          if (!routingChanged) {
                            return Mono.just(saved);
                          }
                          return discoveredServiceService
                              .incrementMembershipVersion(service.id())
                              .thenReturn(saved)
                              .flatMap(
                                  entity ->
                                      runtimePublisher
                                          .publishAffectedApis(service.id())
                                          .thenReturn(entity));
                        }));
  }

  private static boolean isRoutingRelevantChange(
      ServiceInstanceEntity existing, RegisterInstanceRequest body, String scheme, int weight) {
    if ("DEREGISTERED".equals(existing.status())
        || "STALE".equals(existing.status())
        || "DRAINING".equals(existing.status())) {
      return true;
    }
    return !existing.host().equals(body.host())
        || existing.port() != body.port()
        || !existing.scheme().equals(scheme)
        || existing.weight() != weight;
  }

  private Mono<Void> authorize(UUID serviceId, ServerRequest request) {
    return discoveredServiceService
        .getById(serviceId)
        .flatMap(
            service -> {
              if ("OPEN".equals(service.registrationMode())) {
                return Mono.empty();
              }
              String token = request.headers().firstHeader(REGISTRATION_TOKEN_HEADER);
              if (token == null || token.isBlank()) {
                return Mono.error(
                    ControlPlaneException.unauthorized("Registration token required"));
              }
              return credentialService.validateToken(serviceId, token);
            });
  }

  public record RegisterInstanceRequest(
      String instanceId,
      String host,
      Integer port,
      String scheme,
      String zone,
      String region,
      Integer weight,
      Integer leaseDurationSeconds,
      Map<String, String> metadata) {}

  public record HeartbeatRequest(Integer leaseDurationSeconds) {}
}
