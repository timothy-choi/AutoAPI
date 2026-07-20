package com.autoapi.controlplane.policy.bundle;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.managementauth.OrganizationService;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyOverrideEntity;
import com.autoapi.controlplane.persistence.PolicyOverrideRepositoryCustom;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.policy.PolicyOverrideMode;
import com.autoapi.controlplane.policy.PolicyTypeRegistry;
import com.autoapi.controlplane.project.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyOverrideService {

  private final PolicyOverrideRepositoryCustom repository;
  private final PolicyTypeRegistry typeRegistry;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final GatewayGroupRepositoryCustom gatewayGroupRepository;
  private final ApiDefinitionService apiDefinitionService;
  private final RouteRepository routeRepository;
  private final PlatformEventRecorder eventRecorder;
  private final PolicyCacheInvalidator cacheInvalidator;
  private final PolicyAuditService auditService;
  private final ObjectMapper objectMapper;

  public PolicyOverrideService(
      PolicyOverrideRepositoryCustom repository,
      PolicyTypeRegistry typeRegistry,
      OrganizationService organizationService,
      ProjectService projectService,
      GatewayGroupRepositoryCustom gatewayGroupRepository,
      ApiDefinitionService apiDefinitionService,
      RouteRepository routeRepository,
      PlatformEventRecorder eventRecorder,
      PolicyCacheInvalidator cacheInvalidator,
      PolicyAuditService auditService,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.typeRegistry = typeRegistry;
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.gatewayGroupRepository = gatewayGroupRepository;
    this.apiDefinitionService = apiDefinitionService;
    this.routeRepository = routeRepository;
    this.eventRecorder = eventRecorder;
    this.cacheInvalidator = cacheInvalidator;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyOverrideView> create(
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId,
      String policyType,
      String mode,
      JsonNode content,
      EventContext context) {
    validatePolicyType(policyType);
    PolicyOverrideMode overrideMode = PolicyOverrideMode.parse(mode);
    validateContent(overrideMode, content);
    OffsetDateTime now = now();
    return validateScope(scopeLevel, organizationId, projectId, gatewayGroupId, apiId, routeId)
        .then(
            repository.insert(
                scopeLevel,
                organizationId,
                projectId,
                gatewayGroupId,
                apiId,
                routeId,
                policyType,
                overrideMode.name(),
                serializeContent(content),
                now))
        .flatMap(
            override ->
                recordEffectivePolicyChanged(override, context)
                    .then(auditService.recordOverrideAction(context, "OVERRIDE_CREATED", override))
                    .then(cacheInvalidator.invalidate())
                    .thenReturn(PolicyOverrideView.from(override)));
  }

  public Mono<PolicyOverrideView> get(UUID overrideId) {
    return repository
        .findById(overrideId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy override was not found")))
        .map(PolicyOverrideView::from);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyOverrideView> update(
      UUID overrideId, String mode, JsonNode content, EventContext context) {
    OffsetDateTime now = now();
    return get(overrideId)
        .flatMap(
            existing -> {
              String nextMode =
                  mode == null ? existing.mode() : PolicyOverrideMode.parse(mode).name();
              String contentJson =
                  content == null ? existing.contentJson() : serializeContent(content);
              PolicyOverrideMode overrideMode = PolicyOverrideMode.parse(nextMode);
              if (content == null
                  && overrideMode != PolicyOverrideMode.INHERIT
                  && overrideMode != PolicyOverrideMode.DISABLE
                  && (contentJson == null || contentJson.isBlank())) {
                return Mono.error(
                    ControlPlaneException.invalidRequest(
                        "content is required for mode " + nextMode));
              }
              return repository.update(overrideId, nextMode, contentJson, now);
            })
        .flatMap(
            override ->
                recordEffectivePolicyChanged(override, context)
                    .then(auditService.recordOverrideAction(context, "OVERRIDE_UPDATED", override))
                    .then(cacheInvalidator.invalidate())
                    .thenReturn(PolicyOverrideView.from(override)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> delete(UUID overrideId, EventContext context) {
    return repository
        .findById(overrideId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Policy override was not found")))
        .flatMap(
            override ->
                repository
                    .delete(overrideId)
                    .flatMap(
                        deleted -> {
                          if (!deleted) {
                            return Mono.error(
                                ControlPlaneException.notFound("Policy override was not found"));
                          }
                          return recordEffectivePolicyChanged(override, context)
                              .then(
                                  auditService.recordOverrideAction(
                                      context, "OVERRIDE_DELETED", override))
                              .then(cacheInvalidator.invalidate())
                              .then();
                        }));
  }

  public Flux<PolicyOverrideView> listByScope(String scopeLevel, UUID scopeResourceId) {
    Flux<PolicyOverrideEntity> overrides =
        switch (scopeLevel) {
          case "ORGANIZATION" -> repository.findByOrganization(scopeResourceId);
          case "PROJECT" -> repository.findByProject(scopeResourceId);
          case "GATEWAY_GROUP" -> repository.findByGatewayGroup(scopeResourceId);
          case "API" -> repository.findByApi(scopeResourceId);
          case "ROUTE" -> repository.findByRoute(scopeResourceId);
          default ->
              Flux.error(
                  ControlPlaneException.invalidRequest("Unknown scope level: " + scopeLevel));
        };
    return overrides.map(PolicyOverrideView::from);
  }

  private Mono<Void> validateScope(
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId) {
    return switch (scopeLevel) {
      case "ORGANIZATION" ->
          organizationId == null
              ? Mono.error(ControlPlaneException.invalidRequest("organizationId is required"))
              : organizationService.get(organizationId).then();
      case "PROJECT" ->
          projectId == null
              ? Mono.error(ControlPlaneException.invalidRequest("projectId is required"))
              : projectService.get(projectId).then();
      case "GATEWAY_GROUP" ->
          gatewayGroupId == null || projectId == null
              ? Mono.error(
                  ControlPlaneException.invalidRequest("projectId and gatewayGroupId are required"))
              : gatewayGroupRepository
                  .findById(projectId, gatewayGroupId)
                  .switchIfEmpty(
                      Mono.error(ControlPlaneException.notFound("Gateway group was not found")))
                  .then();
      case "API" ->
          apiId == null
              ? Mono.error(ControlPlaneException.invalidRequest("apiId is required"))
              : apiDefinitionService.get(apiId).then();
      case "ROUTE" ->
          routeId == null
              ? Mono.error(ControlPlaneException.invalidRequest("routeId is required"))
              : routeRepository
                  .findById(routeId)
                  .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
                  .then();
      default ->
          Mono.error(ControlPlaneException.invalidRequest("Unknown scope level: " + scopeLevel));
    };
  }

  private void validatePolicyType(String policyType) {
    if (policyType == null || policyType.isBlank()) {
      throw ControlPlaneException.invalidRequest("policyType is required");
    }
    typeRegistry.require(policyType);
  }

  private void validateContent(PolicyOverrideMode mode, JsonNode content) {
    if (mode == PolicyOverrideMode.INHERIT || mode == PolicyOverrideMode.DISABLE) {
      return;
    }
    if (content == null || content.isNull()) {
      throw ControlPlaneException.invalidRequest("content is required for mode " + mode);
    }
  }

  private String serializeContent(JsonNode content) {
    if (content == null || content.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(content);
    } catch (Exception ex) {
      throw ControlPlaneException.invalidRequest("content is not valid JSON");
    }
  }

  private Mono<Void> recordEffectivePolicyChanged(
      PolicyOverrideEntity override, EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("overrideId", override.id().toString());
    payload.put("policyType", override.policyType());
    payload.put("scopeLevel", override.scopeLevel());
    payload.put("mode", override.mode());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.EFFECTIVE_POLICY_CHANGED,
                null,
                override.apiId(),
                "POLICY_OVERRIDE",
                override.id().toString(),
                context,
                payload))
        .then();
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  public record PolicyOverrideView(
      UUID id,
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId,
      String policyType,
      String mode,
      String contentJson,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    static PolicyOverrideView from(PolicyOverrideEntity entity) {
      String content = entity.contentJson() == null ? null : entity.contentJson().asString();
      return new PolicyOverrideView(
          entity.id(),
          entity.scopeLevel(),
          entity.organizationId(),
          entity.projectId(),
          entity.gatewayGroupId(),
          entity.apiId(),
          entity.routeId(),
          entity.policyType(),
          entity.mode(),
          content,
          entity.createdAt(),
          entity.updatedAt());
    }
  }
}
