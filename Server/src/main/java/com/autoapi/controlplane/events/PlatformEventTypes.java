package com.autoapi.controlplane.events;

import java.util.Set;

/** Central registry of versioned platform event type names. */
public final class PlatformEventTypes {

  public static final String PROJECT_CREATED = "project.created.v1";
  public static final String PROJECT_UPDATED = "project.updated.v1";
  public static final String PROJECT_DELETED = "project.deleted.v1";

  public static final String API_CREATED = "api.created.v1";
  public static final String API_UPDATED = "api.updated.v1";
  public static final String API_DELETED = "api.deleted.v1";

  public static final String ROUTE_CREATED = "route.created.v1";
  public static final String ROUTE_UPDATED = "route.updated.v1";
  public static final String ROUTE_DELETED = "route.deleted.v1";

  public static final String CONFIGURATION_VERSION_CREATED = "configuration.version.created.v1";
  public static final String CONFIGURATION_ACTIVATED = "configuration.activated.v1";

  public static final String RUNTIME_SNAPSHOT_PUBLISHED = "runtime.snapshot.published.v1";

  public static final String SERVICE_CREATED = "service.created.v1";
  public static final String SERVICE_UPDATED = "service.updated.v1";
  public static final String SERVICE_DELETED = "service.deleted.v1";

  public static final String SERVICE_INSTANCE_REGISTERED = "service.instance.registered.v1";
  public static final String SERVICE_INSTANCE_DRAINING = "service.instance.draining.v1";
  public static final String SERVICE_INSTANCE_STALE = "service.instance.stale.v1";
  public static final String SERVICE_INSTANCE_RECOVERED = "service.instance.recovered.v1";
  public static final String SERVICE_INSTANCE_DEREGISTERED = "service.instance.deregistered.v1";

  public static final String API_KEY_CREATED = "api_key.created.v1";
  public static final String API_KEY_REVOKED = "api_key.revoked.v1";

  public static final String WEBHOOK_SUBSCRIPTION_CREATED = "webhook.subscription.created.v1";
  public static final String WEBHOOK_SUBSCRIPTION_UPDATED = "webhook.subscription.updated.v1";
  public static final String WEBHOOK_SUBSCRIPTION_DISABLED = "webhook.subscription.disabled.v1";
  public static final String WEBHOOK_SECRET_ROTATED = "webhook.secret.rotated.v1";

  public static final String WEBHOOK_DELIVERY_SUCCEEDED = "webhook.delivery.succeeded.v1";
  public static final String WEBHOOK_DELIVERY_FAILED = "webhook.delivery.failed.v1";
  public static final String WEBHOOK_DELIVERY_DEAD_LETTERED = "webhook.delivery.dead_lettered.v1";

  public static final String WEBHOOK_TEST = "webhook.test.v1";

  public static final String GATEWAY_GROUP_CREATED = "gateway_group.created.v1";
  public static final String GATEWAY_GROUP_UPDATED = "gateway_group.updated.v1";
  public static final String GATEWAY_GROUP_DELETED = "gateway_group.deleted.v1";
  public static final String GATEWAY_GROUP_MEMBERSHIP_CHANGED =
      "gateway_group.membership_changed.v1";

  public static final String RUNTIME_ROLLOUT_CREATED = "runtime_rollout.created.v1";
  public static final String RUNTIME_ROLLOUT_STARTED = "runtime_rollout.started.v1";
  public static final String RUNTIME_ROLLOUT_PAUSED = "runtime_rollout.paused.v1";
  public static final String RUNTIME_ROLLOUT_RESUMED = "runtime_rollout.resumed.v1";
  public static final String RUNTIME_ROLLOUT_CANCELLED = "runtime_rollout.cancelled.v1";
  public static final String RUNTIME_ROLLOUT_FAILED = "runtime_rollout.failed.v1";
  public static final String RUNTIME_ROLLOUT_SUCCEEDED = "runtime_rollout.succeeded.v1";

  public static final String RUNTIME_ROLLOUT_STAGE_STARTED = "runtime_rollout.stage.started.v1";
  public static final String RUNTIME_ROLLOUT_STAGE_OBSERVING = "runtime_rollout.stage.observing.v1";
  public static final String RUNTIME_ROLLOUT_STAGE_SUCCEEDED = "runtime_rollout.stage.succeeded.v1";
  public static final String RUNTIME_ROLLOUT_STAGE_FAILED = "runtime_rollout.stage.failed.v1";

  public static final String RUNTIME_ROLLOUT_ROLLBACK_STARTED =
      "runtime_rollout.rollback.started.v1";
  public static final String RUNTIME_ROLLOUT_ROLLBACK_SUCCEEDED =
      "runtime_rollout.rollback.succeeded.v1";
  public static final String RUNTIME_ROLLOUT_ROLLBACK_FAILED = "runtime_rollout.rollback.failed.v1";

  public static final String ORGANIZATION_CREATED = "organization.created.v1";
  public static final String ORGANIZATION_UPDATED = "organization.updated.v1";
  public static final String ORGANIZATION_SUSPENDED = "organization.suspended.v1";

  public static final String PRINCIPAL_ROLE_BINDING_CREATED = "principal.role_binding.created.v1";
  public static final String PRINCIPAL_ROLE_BINDING_REVOKED = "principal.role_binding.revoked.v1";

  public static final String SERVICE_ACCOUNT_CREATED = "service_account.created.v1";
  public static final String SERVICE_ACCOUNT_UPDATED = "service_account.updated.v1";
  public static final String SERVICE_ACCOUNT_DISABLED = "service_account.disabled.v1";

  public static final String MANAGEMENT_CREDENTIAL_CREATED = "management_credential.created.v1";
  public static final String MANAGEMENT_CREDENTIAL_ROTATED = "management_credential.rotated.v1";
  public static final String MANAGEMENT_CREDENTIAL_REVOKED = "management_credential.revoked.v1";
  public static final String MANAGEMENT_CREDENTIAL_EXPIRED = "management_credential.expired.v1";

  public static final String MANAGEMENT_AUTHENTICATION_FAILED =
      "management_authentication.failed.v1";
  public static final String MANAGEMENT_AUTHORIZATION_DENIED = "management_authorization.denied.v1";
  public static final String MANAGEMENT_BOOTSTRAP_INITIALIZED = "bootstrap_admin_initialized.v1";

  public static final String POLICY_BUNDLE_CREATED = "policy_bundle.created.v1";
  public static final String POLICY_BUNDLE_UPDATED = "policy_bundle.updated.v1";
  public static final String POLICY_BUNDLE_REVISION_CREATED = "policy_bundle.revision.created.v1";
  public static final String POLICY_BUNDLE_ASSIGNED = "policy_bundle.assigned.v1";
  public static final String POLICY_BUNDLE_ASSIGNMENT_REVISION_CHANGED =
      "policy_bundle.assignment.revision_changed.v1";
  public static final String POLICY_BUNDLE_DETACHED = "policy_bundle.detached.v1";
  public static final String POLICY_EVALUATED = "policy.evaluated.v1";
  public static final String EFFECTIVE_POLICY_CHANGED = "effective_policy.changed.v1";

  /** Event types excluded from default webhook fan-out to prevent recursion. */
  public static final Set<String> NON_DELIVERABLE_EVENT_TYPES =
      Set.of(
          WEBHOOK_DELIVERY_SUCCEEDED,
          WEBHOOK_DELIVERY_FAILED,
          WEBHOOK_DELIVERY_DEAD_LETTERED,
          WEBHOOK_TEST);

  private static final Set<String> KNOWN_EVENT_TYPES =
      Set.of(
          PROJECT_CREATED,
          PROJECT_UPDATED,
          PROJECT_DELETED,
          API_CREATED,
          API_UPDATED,
          API_DELETED,
          ROUTE_CREATED,
          ROUTE_UPDATED,
          ROUTE_DELETED,
          CONFIGURATION_VERSION_CREATED,
          CONFIGURATION_ACTIVATED,
          RUNTIME_SNAPSHOT_PUBLISHED,
          SERVICE_CREATED,
          SERVICE_UPDATED,
          SERVICE_DELETED,
          SERVICE_INSTANCE_REGISTERED,
          SERVICE_INSTANCE_DRAINING,
          SERVICE_INSTANCE_STALE,
          SERVICE_INSTANCE_RECOVERED,
          SERVICE_INSTANCE_DEREGISTERED,
          API_KEY_CREATED,
          API_KEY_REVOKED,
          WEBHOOK_SUBSCRIPTION_CREATED,
          WEBHOOK_SUBSCRIPTION_UPDATED,
          WEBHOOK_SUBSCRIPTION_DISABLED,
          WEBHOOK_SECRET_ROTATED,
          WEBHOOK_DELIVERY_SUCCEEDED,
          WEBHOOK_DELIVERY_FAILED,
          WEBHOOK_DELIVERY_DEAD_LETTERED,
          WEBHOOK_TEST,
          GATEWAY_GROUP_CREATED,
          GATEWAY_GROUP_UPDATED,
          GATEWAY_GROUP_DELETED,
          GATEWAY_GROUP_MEMBERSHIP_CHANGED,
          RUNTIME_ROLLOUT_CREATED,
          RUNTIME_ROLLOUT_STARTED,
          RUNTIME_ROLLOUT_PAUSED,
          RUNTIME_ROLLOUT_RESUMED,
          RUNTIME_ROLLOUT_CANCELLED,
          RUNTIME_ROLLOUT_FAILED,
          RUNTIME_ROLLOUT_SUCCEEDED,
          RUNTIME_ROLLOUT_STAGE_STARTED,
          RUNTIME_ROLLOUT_STAGE_OBSERVING,
          RUNTIME_ROLLOUT_STAGE_SUCCEEDED,
          RUNTIME_ROLLOUT_STAGE_FAILED,
          RUNTIME_ROLLOUT_ROLLBACK_STARTED,
          RUNTIME_ROLLOUT_ROLLBACK_SUCCEEDED,
          RUNTIME_ROLLOUT_ROLLBACK_FAILED,
          ORGANIZATION_CREATED,
          ORGANIZATION_UPDATED,
          ORGANIZATION_SUSPENDED,
          PRINCIPAL_ROLE_BINDING_CREATED,
          PRINCIPAL_ROLE_BINDING_REVOKED,
          SERVICE_ACCOUNT_CREATED,
          SERVICE_ACCOUNT_UPDATED,
          SERVICE_ACCOUNT_DISABLED,
          MANAGEMENT_CREDENTIAL_CREATED,
          MANAGEMENT_CREDENTIAL_ROTATED,
          MANAGEMENT_CREDENTIAL_REVOKED,
          MANAGEMENT_CREDENTIAL_EXPIRED,
          MANAGEMENT_AUTHENTICATION_FAILED,
          MANAGEMENT_AUTHORIZATION_DENIED,
          MANAGEMENT_BOOTSTRAP_INITIALIZED,
          POLICY_BUNDLE_CREATED,
          POLICY_BUNDLE_UPDATED,
          POLICY_BUNDLE_REVISION_CREATED,
          POLICY_BUNDLE_ASSIGNED,
          POLICY_BUNDLE_ASSIGNMENT_REVISION_CHANGED,
          POLICY_BUNDLE_DETACHED,
          POLICY_EVALUATED,
          EFFECTIVE_POLICY_CHANGED);

  private PlatformEventTypes() {}

  public static boolean isKnown(String eventType) {
    return KNOWN_EVENT_TYPES.contains(eventType);
  }

  public static void validateFilterType(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("event type filter must not be blank");
    }
    if (!isKnown(eventType) && !eventType.endsWith(".*")) {
      throw new IllegalArgumentException("Unknown event type filter: " + eventType);
    }
  }
}
