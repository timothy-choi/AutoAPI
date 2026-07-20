package com.autoapi.controlplane.policy.bundle;

import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.persistence.PolicyAuditLogEntity;
import com.autoapi.controlplane.persistence.PolicyAuditLogRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleAssignmentEntity;
import com.autoapi.controlplane.persistence.PolicyBundleEntity;
import com.autoapi.controlplane.persistence.PolicyBundleRevisionEntity;
import com.autoapi.controlplane.persistence.PolicyOverrideEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyAuditService {

  private final PolicyAuditLogRepositoryCustom repository;

  public PolicyAuditService(PolicyAuditLogRepositoryCustom repository) {
    this.repository = repository;
  }

  public Mono<PolicyAuditLogEntity> recordBundleAction(
      EventContext context, String action, PolicyBundleEntity bundle, String reason) {
    return repository.insert(
        actorType(context),
        actorId(context),
        action,
        "ORGANIZATION",
        bundle.organizationId(),
        null,
        bundle.id(),
        null,
        null,
        null,
        reason,
        now());
  }

  public Mono<PolicyAuditLogEntity> recordRevisionAction(
      EventContext context, String action, UUID bundleId, PolicyBundleRevisionEntity revision) {
    return repository.insert(
        actorType(context),
        actorId(context),
        action,
        null,
        null,
        null,
        bundleId,
        revision.revisionNumber(),
        null,
        revision.contentJson().asString(),
        null,
        now());
  }

  public Mono<PolicyAuditLogEntity> recordAssignmentAction(
      EventContext context,
      String action,
      PolicyBundleAssignmentEntity assignment,
      String scopeLevel,
      UUID scopeResourceId) {
    return repository.insert(
        actorType(context),
        actorId(context),
        action,
        scopeLevel,
        scopeResourceId,
        null,
        assignment.bundleId(),
        assignment.revisionNumber(),
        null,
        null,
        null,
        now());
  }

  public Mono<PolicyAuditLogEntity> recordAssignmentRevisionUpgrade(
      EventContext context,
      PolicyBundleAssignmentEntity assignment,
      int previousRevisionNumber,
      String scopeLevel,
      UUID scopeResourceId) {
    return repository.insert(
        actorType(context),
        actorId(context),
        "ASSIGNMENT_REVISION_UPGRADED",
        scopeLevel,
        scopeResourceId,
        null,
        assignment.bundleId(),
        assignment.revisionNumber(),
        "{\"revisionNumber\":" + previousRevisionNumber + "}",
        "{\"revisionNumber\":" + assignment.revisionNumber() + "}",
        null,
        now());
  }

  public Mono<PolicyAuditLogEntity> recordDetachAction(
      EventContext context, UUID bundleId, String scopeLevel, UUID scopeResourceId) {
    return repository.insert(
        actorType(context),
        actorId(context),
        "ASSIGNMENT_DETACHED",
        scopeLevel,
        scopeResourceId,
        null,
        bundleId,
        null,
        null,
        null,
        null,
        now());
  }

  public Mono<PolicyAuditLogEntity> recordOverrideAction(
      EventContext context, String action, PolicyOverrideEntity override) {
    return repository.insert(
        actorType(context),
        actorId(context),
        action,
        override.scopeLevel(),
        scopeResourceId(override),
        override.policyType(),
        null,
        null,
        null,
        override.contentJson() == null ? null : override.contentJson().asString(),
        null,
        now());
  }

  private static UUID scopeResourceId(PolicyOverrideEntity override) {
    return switch (override.scopeLevel()) {
      case "ORGANIZATION" -> override.organizationId();
      case "PROJECT" -> override.projectId();
      case "GATEWAY_GROUP" -> override.gatewayGroupId();
      case "API" -> override.apiId();
      case "ROUTE" -> override.routeId();
      default -> null;
    };
  }

  private static String actorType(EventContext context) {
    return context == null ? null : context.actorType();
  }

  private static UUID actorId(EventContext context) {
    if (context == null || context.actorId() == null) {
      return null;
    }
    try {
      return UUID.fromString(context.actorId());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
