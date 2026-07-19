package com.autoapi.controlplane.rollout;

import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.gatewayconfig.GatewayConfigService;
import com.autoapi.controlplane.persistence.GatewayGroupEntity;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom;
import com.autoapi.controlplane.persistence.RuntimeRolloutRepositoryCustom.ActiveRolloutAssignmentRow;
import com.autoapi.controlplane.rollout.EffectiveDesiredConfig.EffectiveDesiredSource;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.ExplicitMembership;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.GroupMembershipContext;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.MembershipKind;
import com.autoapi.controlplane.rollout.GatewayMembershipResolver.ResolvedMembership;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Central effective desired configuration resolution.
 *
 * <p>Precedence: rollback assignment &gt; rollout assignment &gt; group desired &gt; API default.
 */
@Service
@ConditionalOnProperty(
    name = {"autoapi.controlplane.enabled", "autoapi.rollouts.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class EffectiveDesiredConfigService {

  private final RuntimeRolloutRepositoryCustom rolloutRepository;
  private final GatewayGroupRepositoryCustom gatewayGroupRepository;
  private final ApiDefinitionService apiDefinitionService;
  private final GatewayConfigService gatewayConfigService;
  private final ObjectMapper objectMapper;

  public EffectiveDesiredConfigService(
      RuntimeRolloutRepositoryCustom rolloutRepository,
      GatewayGroupRepositoryCustom gatewayGroupRepository,
      ApiDefinitionService apiDefinitionService,
      GatewayConfigService gatewayConfigService,
      ObjectMapper objectMapper) {
    this.rolloutRepository = rolloutRepository;
    this.gatewayGroupRepository = gatewayGroupRepository;
    this.apiDefinitionService = apiDefinitionService;
    this.gatewayConfigService = gatewayConfigService;
    this.objectMapper = objectMapper;
  }

  public Mono<EffectiveDesiredConfig> resolve(String gatewayId, UUID apiId) {
    return rolloutRepository
        .findActiveAssignmentForGateway(gatewayId, apiId)
        .flatMap(active -> resolveFromActiveAssignment(active, apiId))
        .switchIfEmpty(resolveFromGroupOrApiDefault(gatewayId, apiId));
  }

  private Mono<EffectiveDesiredConfig> resolveFromActiveAssignment(
      ActiveRolloutAssignmentRow active, UUID apiId) {
    var assignment = active.assignment();
    String status = assignment.status();
    if ("ROLLBACK_ASSIGNED".equals(status) || "ROLLING_BACK".equals(active.rolloutStatus())) {
      long version = active.sourceVersion();
      return metadataFor(apiId, version)
          .map(
              metadata ->
                  new EffectiveDesiredConfig(
                      apiId,
                      metadata.version(),
                      metadata.contentHash(),
                      metadata.snapshotUrl(),
                      assignment.rolloutId(),
                      assignment.assignedStageIndex(),
                      assignment.rollbackGeneration(),
                      EffectiveDesiredSource.ROLLBACK_ASSIGNMENT));
    }
    if (isRolloutAssignmentStatus(status)) {
      Long target = assignment.targetDesiredVersion();
      if (target == null) {
        target = active.targetVersion();
      }
      long version = target;
      return metadataFor(apiId, version)
          .map(
              metadata ->
                  new EffectiveDesiredConfig(
                      apiId,
                      metadata.version(),
                      metadata.contentHash(),
                      metadata.snapshotUrl(),
                      assignment.rolloutId(),
                      assignment.assignedStageIndex(),
                      assignment.assignmentGeneration(),
                      EffectiveDesiredSource.ROLLOUT_ASSIGNMENT));
    }
    return resolveFromGroupOrApiDefault(assignment.gatewayId(), apiId);
  }

  private Mono<EffectiveDesiredConfig> resolveFromGroupOrApiDefault(String gatewayId, UUID apiId) {
    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api ->
                gatewayGroupRepository
                    .listEnabledByProject(api.projectId())
                    .collectList()
                    .zipWith(
                        gatewayGroupRepository
                            .listMembershipsByProject(api.projectId())
                            .collectList())
                    .flatMap(
                        tuple -> {
                          List<GatewayGroupEntity> groups = tuple.getT1();
                          var memberships = tuple.getT2();
                          Map<String, ExplicitMembership> explicitByGateway = new HashMap<>();
                          for (var membership : memberships) {
                            explicitByGateway.put(
                                membership.gatewayId(),
                                new ExplicitMembership(
                                    membership.gatewayGroupId(),
                                    parseMembershipKind(membership.membershipType())));
                          }
                          return rolloutRepository
                              .listGatewayPlacements()
                              .filter(row -> row.gatewayId().equals(gatewayId))
                              .next()
                              .flatMap(
                                  placement -> {
                                    Map<String, String> effectiveLabels =
                                        GatewayLabelValidator.mergeGatewayLabels(
                                            GatewayMembershipResolver.parseLabels(
                                                objectMapper, placement.adminLabelsJson()),
                                            GatewayMembershipResolver.parseLabels(
                                                objectMapper, placement.labelsJson()));
                                    List<GroupMembershipContext> contexts =
                                        groups.stream()
                                            .filter(group -> group.apiId().equals(apiId))
                                            .map(
                                                group ->
                                                    new GroupMembershipContext(
                                                        group.id(),
                                                        group.projectId(),
                                                        group.apiId(),
                                                        group.name(),
                                                        group.enabled(),
                                                        parseSelector(group.selectorJson()),
                                                        group.desiredConfigVersion()))
                                            .toList();
                                    ResolvedMembership resolved =
                                        GatewayMembershipResolver.resolve(
                                            gatewayId,
                                            effectiveLabels,
                                            contexts,
                                            explicitByGateway);
                                    if (resolved.group() != null
                                        && resolved.group().desiredConfigVersion() != null) {
                                      long version = resolved.group().desiredConfigVersion();
                                      return metadataFor(apiId, version)
                                          .map(
                                              metadata ->
                                                  new EffectiveDesiredConfig(
                                                      apiId,
                                                      metadata.version(),
                                                      metadata.contentHash(),
                                                      metadata.snapshotUrl(),
                                                      null,
                                                      null,
                                                      0L,
                                                      EffectiveDesiredSource.GROUP_DESIRED));
                                    }
                                    return apiDefault(apiId);
                                  })
                              .switchIfEmpty(apiDefault(apiId));
                        }));
  }

  private Mono<EffectiveDesiredConfig> apiDefault(UUID apiId) {
    return gatewayConfigService
        .getDesiredMetadata(apiId)
        .map(
            metadata ->
                new EffectiveDesiredConfig(
                    apiId,
                    metadata.version(),
                    metadata.contentHash(),
                    metadata.snapshotUrl(),
                    null,
                    null,
                    0L,
                    EffectiveDesiredSource.API_DEFAULT));
  }

  private Mono<GatewayConfigService.DesiredConfigMetadata> metadataFor(UUID apiId, long version) {
    return gatewayConfigService
        .getSnapshotEntity(apiId, version)
        .map(
            entity ->
                new GatewayConfigService.DesiredConfigMetadata(
                    apiId,
                    entity.version(),
                    entity.contentHash(),
                    GatewayConfigService.snapshotPath(apiId, entity.version())));
  }

  private static boolean isRolloutAssignmentStatus(String status) {
    return switch (status) {
      case "PENDING", "ASSIGNED", "DELIVERED", "ACKNOWLEDGED", "ACTIVATED", "FAILED", "TIMED_OUT" ->
          true;
      default -> false;
    };
  }

  private JsonNode parseSelector(String selectorJson) {
    try {
      return objectMapper.readTree(selectorJson == null ? "{}" : selectorJson);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private static MembershipKind parseMembershipKind(String membershipType) {
    return switch (membershipType) {
      case "EXPLICIT_INCLUDE" -> MembershipKind.EXPLICIT_INCLUDE;
      case "EXPLICIT_EXCLUDE" -> MembershipKind.EXPLICIT_EXCLUDE;
      default -> MembershipKind.SELECTOR;
    };
  }
}
