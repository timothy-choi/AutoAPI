package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.persistence.GatewayGroupEntity;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom.GatewayGroupMembershipRow;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import com.autoapi.controlplane.project.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class GatewayGroupService {

  private static final Logger log = LoggerFactory.getLogger(GatewayGroupService.class);
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PREVIEW_GATEWAYS = 20;

  private final GatewayGroupRepositoryCustom repository;
  private final RuntimeRolloutRepositoryCustom rolloutRepository;
  private final ProjectService projectService;
  private final ApiDefinitionService apiDefinitionService;
  private final PlatformEventRecorder eventRecorder;
  private final RolloutsProperties properties;
  private final ControlPlaneProperties controlPlaneProperties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public GatewayGroupService(
      GatewayGroupRepositoryCustom repository,
      RuntimeRolloutRepositoryCustom rolloutRepository,
      ProjectService projectService,
      ApiDefinitionService apiDefinitionService,
      PlatformEventRecorder eventRecorder,
      RolloutsProperties properties,
      ControlPlaneProperties controlPlaneProperties,
      ObjectMapper objectMapper,
      Clock clock) {
    this.repository = repository;
    this.rolloutRepository = rolloutRepository;
    this.projectService = projectService;
    this.apiDefinitionService = apiDefinitionService;
    this.eventRecorder = eventRecorder;
    this.properties = properties;
    this.controlPlaneProperties = controlPlaneProperties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<GatewayGroupView> create(
      UUID projectId,
      UUID apiId,
      String name,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      Boolean enabled,
      EventContext context) {
    OffsetDateTime now = now();
    return projectService
        .get(projectId)
        .then(apiDefinitionService.get(apiId))
        .flatMap(
            api -> {
              if (!api.projectId().equals(projectId)) {
                return Mono.error(
                    ControlPlaneException.invalidRequest("API does not belong to project"));
              }
              validateSelector(selectorJson);
              return repository
                  .insert(
                      projectId,
                      apiId,
                      name,
                      description,
                      region,
                      zone,
                      environment,
                      selectorJson,
                      enabled == null || enabled,
                      now)
                  .flatMap(
                      group ->
                          recordGroupEvent(group, PlatformEventTypes.GATEWAY_GROUP_CREATED, context)
                              .thenReturn(GatewayGroupView.from(group)))
                  .doOnSuccess(
                      view ->
                          log.info(
                              "gateway_group_created groupId={} projectId={} apiId={}",
                              view.id(),
                              projectId,
                              apiId));
            })
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex -> ControlPlaneException.conflict("Gateway group name already exists"));
  }

  public Flux<GatewayGroupView> list(UUID projectId, int limit, int offset) {
    int effectiveLimit = boundedLimit(limit);
    return projectService
        .get(projectId)
        .thenMany(repository.listByProject(projectId, effectiveLimit, Math.max(offset, 0)))
        .map(GatewayGroupView::from);
  }

  public Mono<GatewayGroupView> get(UUID projectId, UUID groupId) {
    return repository
        .findById(projectId, groupId)
        .map(GatewayGroupView::from)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway group was not found")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<GatewayGroupView> update(
      UUID projectId,
      UUID groupId,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      Boolean enabled,
      Long desiredConfigVersion,
      long expectedVersion,
      EventContext context) {
    if (selectorJson != null) {
      validateSelector(selectorJson);
    }
    return repository
        .update(
            projectId,
            groupId,
            description,
            region,
            zone,
            environment,
            selectorJson,
            enabled,
            desiredConfigVersion,
            expectedVersion,
            now())
        .switchIfEmpty(
            Mono.error(
                ControlPlaneException.conflict("Gateway group update conflict or not found")))
        .flatMap(
            group ->
                recordGroupEvent(group, PlatformEventTypes.GATEWAY_GROUP_UPDATED, context)
                    .thenReturn(GatewayGroupView.from(group)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> delete(UUID projectId, UUID groupId, EventContext context) {
    return repository
        .findById(projectId, groupId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway group was not found")))
        .flatMap(
            group ->
                repository
                    .hasActiveRollout(groupId)
                    .flatMap(
                        active -> {
                          if (active) {
                            return Mono.error(
                                ControlPlaneException.conflict(
                                    "Gateway group has an active rollout"));
                          }
                          return recordGroupEvent(
                                  group, PlatformEventTypes.GATEWAY_GROUP_DELETED, context)
                              .then(repository.softDelete(projectId, groupId, now()))
                              .then();
                        }));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> addExplicitMembership(
      UUID projectId, UUID groupId, String gatewayId, EventContext context) {
    return requireGroup(projectId, groupId)
        .flatMap(
            group ->
                repository
                    .hasActiveRollout(groupId)
                    .flatMap(
                        active -> {
                          if (active) {
                            return Mono.error(
                                ControlPlaneException.conflict(
                                    "Cannot change membership during active rollout"));
                          }
                          return repository
                              .upsertExplicitMembership(
                                  projectId, groupId, gatewayId, "EXPLICIT_INCLUDE", now())
                              .then(
                                  recordMembershipEvent(
                                      projectId, groupId, gatewayId, "EXPLICIT_INCLUDE", context));
                        }));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> removeExplicitMembership(
      UUID projectId, UUID groupId, String gatewayId, EventContext context) {
    return requireGroup(projectId, groupId)
        .flatMap(
            group ->
                repository
                    .hasActiveRollout(groupId)
                    .flatMap(
                        active -> {
                          if (active) {
                            return Mono.error(
                                ControlPlaneException.conflict(
                                    "Cannot change membership during active rollout"));
                          }
                          return repository
                              .removeExplicitMembership(projectId, groupId, gatewayId)
                              .then(
                                  recordMembershipEvent(
                                      projectId, groupId, gatewayId, "REMOVED", context));
                        }));
  }

  public Flux<GroupGatewayView> listGroupGateways(UUID projectId, UUID groupId) {
    return requireGroup(projectId, groupId)
        .flatMapMany(
            group ->
                resolveMembershipContext(projectId, group.apiId())
                    .flatMapMany(
                        ctx ->
                            rolloutRepository
                                .listGatewayPlacements()
                                .map(
                                    placement -> {
                                      ResolvedMembershipSnapshot membership =
                                          resolveGatewayMembership(
                                              placement.gatewayId(),
                                              placement,
                                              ctx.groups(),
                                              ctx.explicitByGateway());
                                      if (!groupId.equals(membership.gatewayGroupId())) {
                                        return null;
                                      }
                                      return GroupGatewayView.from(
                                          placement, membership, isLive(placement.lastSeenAt()));
                                    })
                                .filter(view -> view != null)));
  }

  public Mono<MembershipPreview> previewMembership(UUID projectId, UUID groupId) {
    return requireGroup(projectId, groupId)
        .flatMap(
            group ->
                resolveMembershipContext(projectId, group.apiId())
                    .flatMap(
                        ctx ->
                            rolloutRepository
                                .listGatewayPlacements()
                                .map(
                                    placement ->
                                        resolveGatewayMembership(
                                            placement.gatewayId(),
                                            placement,
                                            ctx.groups(),
                                            ctx.explicitByGateway()))
                                .filter(snapshot -> groupId.equals(snapshot.gatewayGroupId()))
                                .take(MAX_PREVIEW_GATEWAYS)
                                .collectList()
                                .map(
                                    members ->
                                        new MembershipPreview(
                                            groupId,
                                            members.size(),
                                            members.stream()
                                                .map(ResolvedMembershipSnapshot::gatewayId)
                                                .toList()))));
  }

  public Mono<GroupConvergenceSummary> convergenceSummary(UUID projectId, UUID groupId) {
    OffsetDateTime staleCutoff = now().minus(controlPlaneProperties.gatewayStaleAfter());
    return requireGroup(projectId, groupId)
        .flatMap(
            group ->
                listGroupGateways(projectId, groupId)
                    .collectList()
                    .map(
                        gateways -> {
                          int live = 0;
                          int stale = 0;
                          for (GroupGatewayView gateway : gateways) {
                            if ("LIVE".equals(gateway.liveness())) {
                              live++;
                            } else {
                              stale++;
                            }
                          }
                          return new GroupConvergenceSummary(
                              groupId,
                              group.apiId(),
                              group.desiredConfigVersion(),
                              gateways.size(),
                              live,
                              stale,
                              staleCutoff.toString());
                        }));
  }

  private Mono<GatewayGroupEntity> requireGroup(UUID projectId, UUID groupId) {
    return repository
        .findById(projectId, groupId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway group was not found")));
  }

  private Mono<MembershipContext> resolveMembershipContext(UUID projectId, UUID apiId) {
    return repository
        .listEnabledByProject(projectId)
        .filter(group -> group.apiId().equals(apiId))
        .map(this::toGroupContext)
        .collectList()
        .zipWith(repository.listMembershipsByProject(projectId).collectList())
        .map(
            tuple -> {
              Map<String, GatewayMembershipResolver.ExplicitMembership> explicitByGateway =
                  new HashMap<>();
              for (GatewayGroupMembershipRow membership : tuple.getT2()) {
                explicitByGateway.put(
                    membership.gatewayId(),
                    new GatewayMembershipResolver.ExplicitMembership(
                        membership.gatewayGroupId(),
                        parseMembershipKind(membership.membershipType())));
              }
              return new MembershipContext(tuple.getT1(), explicitByGateway);
            });
  }

  private ResolvedMembershipSnapshot resolveGatewayMembership(
      String gatewayId,
      RuntimeRolloutRepositoryCustom.GatewayPlacementRow placement,
      List<GatewayMembershipResolver.GroupMembershipContext> groups,
      Map<String, GatewayMembershipResolver.ExplicitMembership> explicitByGateway) {
    Map<String, String> effectiveLabels =
        GatewayLabelValidator.mergeGatewayLabels(
            GatewayMembershipResolver.parseLabels(objectMapper, placement.adminLabelsJson()),
            GatewayMembershipResolver.parseLabels(objectMapper, placement.labelsJson()));
    GatewayMembershipResolver.ResolvedMembership resolved =
        GatewayMembershipResolver.resolve(gatewayId, effectiveLabels, groups, explicitByGateway);
    return new ResolvedMembershipSnapshot(
        gatewayId, resolved.gatewayGroupId(), resolved.membershipKind().name(), effectiveLabels);
  }

  private GatewayMembershipResolver.GroupMembershipContext toGroupContext(
      GatewayGroupEntity group) {
    return new GatewayMembershipResolver.GroupMembershipContext(
        group.id(),
        group.projectId(),
        group.apiId(),
        group.name(),
        group.enabled(),
        parseSelector(group.selectorJson()),
        group.desiredConfigVersion());
  }

  private void validateSelector(String selectorJson) {
    if (selectorJson == null || selectorJson.isBlank()) {
      return;
    }
    try {
      JsonNode node = objectMapper.readTree(selectorJson);
      if (!node.isObject()) {
        throw ControlPlaneException.invalidRequest("selector must be a JSON object");
      }
    } catch (ControlPlaneException ex) {
      throw ex;
    } catch (Exception ex) {
      throw ControlPlaneException.invalidRequest("selector must be valid JSON");
    }
  }

  private boolean isLive(OffsetDateTime lastSeenAt) {
    if (lastSeenAt == null) {
      return false;
    }
    return !lastSeenAt.isBefore(now().minus(controlPlaneProperties.gatewayStaleAfter()));
  }

  private Mono<Void> recordGroupEvent(
      GatewayGroupEntity group, String eventType, EventContext context) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                eventType,
                group.projectId(),
                group.apiId(),
                "GATEWAY_GROUP",
                group.id().toString(),
                context,
                Map.of(
                    "groupId", group.id().toString(),
                    "name", group.name(),
                    "apiId", group.apiId().toString(),
                    "enabled", group.enabled())))
        .then();
  }

  private Mono<Void> recordMembershipEvent(
      UUID projectId, UUID groupId, String gatewayId, String changeType, EventContext context) {
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.GATEWAY_GROUP_MEMBERSHIP_CHANGED,
                projectId,
                null,
                "GATEWAY_GROUP",
                groupId.toString(),
                context,
                Map.of(
                    "groupId", groupId.toString(),
                    "gatewayId", gatewayId,
                    "changeType", changeType)))
        .then();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
  }

  private static int boundedLimit(int limit) {
    if (limit <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(limit, 200);
  }

  private JsonNode parseSelector(String selectorJson) {
    try {
      return objectMapper.readTree(selectorJson == null ? "{}" : selectorJson);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private static GatewayMembershipResolver.MembershipKind parseMembershipKind(
      String membershipType) {
    return switch (membershipType) {
      case "EXPLICIT_INCLUDE" -> GatewayMembershipResolver.MembershipKind.EXPLICIT_INCLUDE;
      case "EXPLICIT_EXCLUDE" -> GatewayMembershipResolver.MembershipKind.EXPLICIT_EXCLUDE;
      default -> GatewayMembershipResolver.MembershipKind.SELECTOR;
    };
  }

  private record MembershipContext(
      List<GatewayMembershipResolver.GroupMembershipContext> groups,
      Map<String, GatewayMembershipResolver.ExplicitMembership> explicitByGateway) {}

  public record GatewayGroupView(
      UUID id,
      UUID projectId,
      UUID apiId,
      String name,
      String description,
      String region,
      String zone,
      String environment,
      String selectorJson,
      boolean enabled,
      Long desiredConfigVersion,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      long version) {

    static GatewayGroupView from(GatewayGroupEntity entity) {
      return new GatewayGroupView(
          entity.id(),
          entity.projectId(),
          entity.apiId(),
          entity.name(),
          entity.description(),
          entity.region(),
          entity.zone(),
          entity.environment(),
          entity.selectorJson(),
          entity.enabled(),
          entity.desiredConfigVersion(),
          entity.createdAt(),
          entity.updatedAt(),
          entity.version());
    }
  }

  public record GroupGatewayView(
      String gatewayId, String membershipKind, String liveness, Map<String, String> labels) {

    static GroupGatewayView from(
        RuntimeRolloutRepositoryCustom.GatewayPlacementRow placement,
        ResolvedMembershipSnapshot membership,
        boolean live) {
      return new GroupGatewayView(
          placement.gatewayId(),
          membership.membershipKind(),
          live ? "LIVE" : "STALE",
          membership.labels());
    }
  }

  public record ResolvedMembershipSnapshot(
      String gatewayId, UUID gatewayGroupId, String membershipKind, Map<String, String> labels) {}

  public record MembershipPreview(UUID groupId, int matchedCount, List<String> sampleGatewayIds) {}

  public record GroupConvergenceSummary(
      UUID groupId,
      UUID apiId,
      Long desiredConfigVersion,
      int memberCount,
      int liveGatewayCount,
      int staleGatewayCount,
      String staleCutoff) {}
}
