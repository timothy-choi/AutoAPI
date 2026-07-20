package com.autoapi.controlplane.policy.bundle;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import com.autoapi.controlplane.managementauth.OrganizationService;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleAssignmentEntity;
import com.autoapi.controlplane.persistence.PolicyBundleAssignmentRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleRepositoryCustom;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.autoapi.controlplane.project.ProjectService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyBundleAssignmentService {

  private final PolicyBundleAssignmentRepositoryCustom assignmentRepository;
  private final PolicyBundleRepositoryCustom bundleRepository;
  private final PolicyBundleService bundleService;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final GatewayGroupRepositoryCustom gatewayGroupRepository;
  private final ApiDefinitionService apiDefinitionService;
  private final RouteRepository routeRepository;
  private final PlatformEventRecorder eventRecorder;
  private final PolicyCacheInvalidator cacheInvalidator;
  private final PolicyAuditService auditService;

  public PolicyBundleAssignmentService(
      PolicyBundleAssignmentRepositoryCustom assignmentRepository,
      PolicyBundleRepositoryCustom bundleRepository,
      PolicyBundleService bundleService,
      OrganizationService organizationService,
      ProjectService projectService,
      GatewayGroupRepositoryCustom gatewayGroupRepository,
      ApiDefinitionService apiDefinitionService,
      RouteRepository routeRepository,
      PlatformEventRecorder eventRecorder,
      PolicyCacheInvalidator cacheInvalidator,
      PolicyAuditService auditService) {
    this.assignmentRepository = assignmentRepository;
    this.bundleRepository = bundleRepository;
    this.bundleService = bundleService;
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.gatewayGroupRepository = gatewayGroupRepository;
    this.apiDefinitionService = apiDefinitionService;
    this.routeRepository = routeRepository;
    this.eventRecorder = eventRecorder;
    this.cacheInvalidator = cacheInvalidator;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> assignAtOrganization(
      UUID organizationId, UUID bundleId, Integer revisionNumber, EventContext context) {
    return organizationService
        .get(organizationId)
        .then(bundleService.requireBundle(organizationId, bundleId))
        .flatMap(bundle -> resolveRevisionNumber(bundleId, revisionNumber))
        .flatMap(
            revision ->
                assignmentRepository.insert(
                    bundleId,
                    revision,
                    "ORGANIZATION",
                    organizationId,
                    null,
                    null,
                    null,
                    null,
                    true,
                    now()))
        .flatMap(assignment -> finalizeAssign(assignment, context, "ORGANIZATION", organizationId))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict(
                        "An enabled organization assignment already exists for this bundle")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> assignAtProject(
      UUID projectId, UUID bundleId, Integer revisionNumber, EventContext context) {
    return projectService
        .get(projectId)
        .flatMap(
            project ->
                bundleService
                    .requireBundle(project.organizationId(), bundleId)
                    .then(resolveRevisionNumber(bundleId, revisionNumber))
                    .flatMap(
                        revision ->
                            assignmentRepository.insert(
                                bundleId,
                                revision,
                                "PROJECT",
                                project.organizationId(),
                                projectId,
                                null,
                                null,
                                null,
                                true,
                                now()))
                    .flatMap(
                        assignment -> finalizeAssign(assignment, context, "PROJECT", projectId)));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> assignAtGatewayGroup(
      UUID projectId, UUID groupId, UUID bundleId, Integer revisionNumber, EventContext context) {
    return projectService
        .get(projectId)
        .flatMap(
            project ->
                gatewayGroupRepository
                    .findById(projectId, groupId)
                    .switchIfEmpty(
                        Mono.error(ControlPlaneException.notFound("Gateway group was not found")))
                    .flatMap(
                        group ->
                            bundleService
                                .requireBundle(project.organizationId(), bundleId)
                                .then(resolveRevisionNumber(bundleId, revisionNumber))
                                .flatMap(
                                    revision ->
                                        assignmentRepository.insert(
                                            bundleId,
                                            revision,
                                            "GATEWAY_GROUP",
                                            project.organizationId(),
                                            projectId,
                                            group.id(),
                                            null,
                                            null,
                                            true,
                                            now()))))
        .flatMap(assignment -> finalizeAssign(assignment, context, "GATEWAY_GROUP", groupId));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> assignAtApi(
      UUID apiId, UUID bundleId, Integer revisionNumber, EventContext context) {
    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api ->
                projectService
                    .get(api.projectId())
                    .flatMap(
                        project ->
                            bundleService
                                .requireBundle(project.organizationId(), bundleId)
                                .then(resolveRevisionNumber(bundleId, revisionNumber))
                                .flatMap(
                                    revision ->
                                        assignmentRepository.insert(
                                            bundleId,
                                            revision,
                                            "API",
                                            project.organizationId(),
                                            project.id(),
                                            null,
                                            apiId,
                                            null,
                                            true,
                                            now()))))
        .flatMap(assignment -> finalizeAssign(assignment, context, "API", apiId))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict(
                        "An enabled API assignment already exists for this bundle")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> assignAtRoute(
      UUID routeId, UUID bundleId, Integer revisionNumber, EventContext context) {
    return routeRepository
        .findById(routeId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Route was not found")))
        .flatMap(
            route ->
                apiDefinitionService
                    .get(route.apiId())
                    .flatMap(
                        api ->
                            projectService
                                .get(api.projectId())
                                .flatMap(
                                    project ->
                                        bundleService
                                            .requireBundle(project.organizationId(), bundleId)
                                            .then(resolveRevisionNumber(bundleId, revisionNumber))
                                            .flatMap(
                                                revision ->
                                                    assignmentRepository.insert(
                                                        bundleId,
                                                        revision,
                                                        "ROUTE",
                                                        project.organizationId(),
                                                        project.id(),
                                                        null,
                                                        api.id(),
                                                        routeId,
                                                        true,
                                                        now())))))
        .flatMap(assignment -> finalizeAssign(assignment, context, "ROUTE", routeId))
        .onErrorResume(
            DataIntegrityViolationException.class,
            ex ->
                Mono.error(
                    ControlPlaneException.conflict(
                        "An enabled route assignment already exists for this bundle")));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> detachAtOrganization(UUID organizationId, UUID bundleId, EventContext context) {
    return finalizeDetach(
        assignmentRepository.disableByScope(bundleId, "ORGANIZATION", organizationId, now()),
        bundleId,
        context,
        "ORGANIZATION",
        organizationId);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> detachAtProject(UUID projectId, UUID bundleId, EventContext context) {
    return finalizeDetach(
        assignmentRepository.disableByScope(bundleId, "PROJECT", projectId, now()),
        bundleId,
        context,
        "PROJECT",
        projectId);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> detachAtGatewayGroup(
      UUID projectId, UUID groupId, UUID bundleId, EventContext context) {
    return gatewayGroupRepository
        .findById(projectId, groupId)
        .switchIfEmpty(Mono.error(ControlPlaneException.notFound("Gateway group was not found")))
        .then(
            finalizeDetach(
                assignmentRepository.disableByScope(bundleId, "GATEWAY_GROUP", groupId, now()),
                bundleId,
                context,
                "GATEWAY_GROUP",
                groupId));
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> detachAtApi(UUID apiId, UUID bundleId, EventContext context) {
    return finalizeDetach(
        assignmentRepository.disableByScope(bundleId, "API", apiId, now()),
        bundleId,
        context,
        "API",
        apiId);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<Void> detachAtRoute(UUID routeId, UUID bundleId, EventContext context) {
    return finalizeDetach(
        assignmentRepository.disableByScope(bundleId, "ROUTE", routeId, now()),
        bundleId,
        context,
        "ROUTE",
        routeId);
  }

  public Flux<PolicyBundleAssignmentView> listAtScope(
      String scopeLevel, UUID scopeResourceId, int limit, int offset) {
    return assignmentRepository
        .listByScope(scopeLevel, scopeResourceId, limit, offset)
        .map(PolicyBundleAssignmentView::from);
  }

  public Mono<PolicyBundleAssignmentView> get(UUID assignmentId) {
    return assignmentRepository
        .findById(assignmentId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Policy bundle assignment was not found")))
        .map(PolicyBundleAssignmentView::from);
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<PolicyBundleAssignmentView> upgradeRevision(
      UUID assignmentId, Integer revisionNumber, EventContext context) {
    if (revisionNumber == null || revisionNumber <= 0) {
      return Mono.error(
          ControlPlaneException.invalidRequest("revisionNumber must be a positive integer"));
    }
    return assignmentRepository
        .findById(assignmentId)
        .switchIfEmpty(
            Mono.error(ControlPlaneException.notFound("Policy bundle assignment was not found")))
        .flatMap(
            existing -> {
              if (!existing.enabled()) {
                return Mono.error(
                    ControlPlaneException.notFound("Policy bundle assignment was not found"));
              }
              if (existing.revisionNumber() == revisionNumber) {
                return Mono.just(new RevisionUpgrade(existing.revisionNumber(), existing));
              }
              return bundleService
                  .requireBundle(existing.organizationId(), existing.bundleId())
                  .then(resolveRevisionNumber(existing.bundleId(), revisionNumber))
                  .flatMap(
                      resolvedRevision ->
                          assignmentRepository.updateRevision(
                              assignmentId, resolvedRevision, now()))
                  .switchIfEmpty(
                      Mono.error(
                          ControlPlaneException.notFound("Policy bundle assignment was not found")))
                  .map(updated -> new RevisionUpgrade(existing.revisionNumber(), updated));
            })
        .flatMap(
            upgrade ->
                upgrade.updated().revisionNumber() == upgrade.previousRevision()
                    ? Mono.just(PolicyBundleAssignmentView.from(upgrade.updated()))
                    : finalizeRevisionUpgrade(upgrade, context));
  }

  private Mono<PolicyBundleAssignmentView> finalizeRevisionUpgrade(
      RevisionUpgrade upgrade, EventContext context) {
    PolicyBundleAssignmentEntity updated = upgrade.updated();
    UUID scopeResourceId = scopeResourceId(updated);
    return recordRevisionChangedEvent(upgrade.previousRevision(), updated, context)
        .then(
            auditService.recordAssignmentRevisionUpgrade(
                context,
                updated,
                upgrade.previousRevision(),
                updated.scopeLevel(),
                scopeResourceId))
        .then(cacheInvalidator.invalidate())
        .then(recordEffectivePolicyChanged(updated, context))
        .thenReturn(PolicyBundleAssignmentView.from(updated));
  }

  private static UUID scopeResourceId(PolicyBundleAssignmentEntity assignment) {
    return switch (assignment.scopeLevel()) {
      case "ORGANIZATION" -> assignment.organizationId();
      case "PROJECT" -> assignment.projectId();
      case "GATEWAY_GROUP" -> assignment.gatewayGroupId();
      case "API" -> assignment.apiId();
      case "ROUTE" -> assignment.routeId();
      default -> null;
    };
  }

  private Mono<Void> recordRevisionChangedEvent(
      int previousRevision, PolicyBundleAssignmentEntity assignment, EventContext context) {
    Map<String, Object> payload = assignmentPayload(assignment);
    payload.put("previousRevisionNumber", previousRevision);
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.POLICY_BUNDLE_ASSIGNMENT_REVISION_CHANGED,
                null,
                assignment.apiId(),
                "POLICY_BUNDLE_ASSIGNMENT",
                assignment.id().toString(),
                context,
                payload))
        .then();
  }

  private record RevisionUpgrade(int previousRevision, PolicyBundleAssignmentEntity updated) {}

  private Mono<Integer> resolveRevisionNumber(UUID bundleId, Integer revisionNumber) {
    if (revisionNumber != null && revisionNumber > 0) {
      return bundleRepository
          .findRevision(bundleId, revisionNumber)
          .switchIfEmpty(
              Mono.error(
                  ControlPlaneException.notFound(
                      "Policy bundle revision " + revisionNumber + " was not found")))
          .thenReturn(revisionNumber);
    }
    return bundleRepository
        .listRevisions(bundleId, 1, 0)
        .next()
        .map(revision -> revision.revisionNumber())
        .switchIfEmpty(
            Mono.error(
                ControlPlaneException.invalidRequest(
                    "Policy bundle has no revisions; create a revision first")));
  }

  private Mono<PolicyBundleAssignmentView> finalizeAssign(
      PolicyBundleAssignmentEntity assignment,
      EventContext context,
      String scopeLevel,
      UUID scopeResourceId) {
    return recordAssignedEvent(assignment, context)
        .then(
            auditService.recordAssignmentAction(
                context, "ASSIGNMENT_CREATED", assignment, scopeLevel, scopeResourceId))
        .then(cacheInvalidator.invalidate())
        .then(recordEffectivePolicyChanged(assignment, context))
        .thenReturn(PolicyBundleAssignmentView.from(assignment));
  }

  private Mono<Void> finalizeDetach(
      Mono<Boolean> disabled,
      UUID bundleId,
      EventContext context,
      String scopeLevel,
      UUID scopeResourceId) {
    return disabled.flatMap(
        success ->
            success
                ? recordDetachedEvent(bundleId, scopeLevel, scopeResourceId, context)
                    .then(
                        auditService.recordDetachAction(
                            context, bundleId, scopeLevel, scopeResourceId))
                    .then(cacheInvalidator.invalidate())
                    .then(recordEffectivePolicyChangedDetached(bundleId, context))
                : Mono.error(
                    ControlPlaneException.notFound("Policy bundle assignment was not found")));
  }

  private Mono<Void> recordAssignedEvent(
      PolicyBundleAssignmentEntity assignment, EventContext context) {
    Map<String, Object> payload = assignmentPayload(assignment);
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.POLICY_BUNDLE_ASSIGNED,
                null,
                assignment.apiId(),
                "POLICY_BUNDLE_ASSIGNMENT",
                assignment.id().toString(),
                context,
                payload))
        .then();
  }

  private Mono<Void> recordDetachedEvent(
      UUID bundleId, String scopeLevel, UUID scopeResourceId, EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("bundleId", bundleId.toString());
    payload.put("scopeLevel", scopeLevel);
    payload.put("scopeResourceId", scopeResourceId.toString());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.POLICY_BUNDLE_DETACHED,
                null,
                null,
                "POLICY_BUNDLE_ASSIGNMENT",
                bundleId.toString(),
                context,
                payload))
        .then();
  }

  private Mono<Void> recordEffectivePolicyChanged(
      PolicyBundleAssignmentEntity assignment, EventContext context) {
    Map<String, Object> payload = assignmentPayload(assignment);
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.EFFECTIVE_POLICY_CHANGED,
                null,
                assignment.apiId(),
                "EFFECTIVE_POLICY",
                assignment.apiId() == null
                    ? assignment.id().toString()
                    : assignment.apiId().toString(),
                context,
                payload))
        .then();
  }

  private Mono<Void> recordEffectivePolicyChangedDetached(UUID bundleId, EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("bundleId", bundleId.toString());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.EFFECTIVE_POLICY_CHANGED,
                null,
                null,
                "EFFECTIVE_POLICY",
                bundleId.toString(),
                context,
                payload))
        .then();
  }

  private static Map<String, Object> assignmentPayload(PolicyBundleAssignmentEntity assignment) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("assignmentId", assignment.id().toString());
    payload.put("bundleId", assignment.bundleId().toString());
    payload.put("revisionNumber", assignment.revisionNumber());
    payload.put("scopeLevel", assignment.scopeLevel());
    return payload;
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  public record PolicyBundleAssignmentView(
      UUID id,
      UUID bundleId,
      int revisionNumber,
      String scopeLevel,
      UUID organizationId,
      UUID projectId,
      UUID gatewayGroupId,
      UUID apiId,
      UUID routeId,
      boolean enabled,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    static PolicyBundleAssignmentView from(PolicyBundleAssignmentEntity entity) {
      return new PolicyBundleAssignmentView(
          entity.id(),
          entity.bundleId(),
          entity.revisionNumber(),
          entity.scopeLevel(),
          entity.organizationId(),
          entity.projectId(),
          entity.gatewayGroupId(),
          entity.apiId(),
          entity.routeId(),
          entity.enabled(),
          entity.createdAt(),
          entity.updatedAt());
    }
  }
}
