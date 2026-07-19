package com.autoapi.controlplane.managementauth;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Stable catalog of management-plane permissions. */
public enum ManagementPermission {
  ORGANIZATION_READ("organization.read"),
  ORGANIZATION_MANAGE("organization.manage"),
  ORGANIZATION_MEMBERS_MANAGE("organization.members.manage"),

  PROJECT_CREATE("project.create"),
  PROJECT_READ("project.read"),
  PROJECT_UPDATE("project.update"),
  PROJECT_DELETE("project.delete"),
  PROJECT_MEMBERS_MANAGE("project.members.manage"),

  API_READ("api.read"),
  API_MANAGE("api.manage"),

  ROUTE_READ("route.read"),
  ROUTE_MANAGE("route.manage"),

  POLICY_READ("policy.read"),
  POLICY_MANAGE("policy.manage"),

  CONFIGURATION_READ("configuration.read"),
  CONFIGURATION_CREATE("configuration.create"),
  CONFIGURATION_ACTIVATE("configuration.activate"),
  CONFIGURATION_ROLLBACK("configuration.rollback"),

  GATEWAY_READ("gateway.read"),
  GATEWAY_MANAGE("gateway.manage"),

  GATEWAY_GROUP_READ("gateway_group.read"),
  GATEWAY_GROUP_MANAGE("gateway_group.manage"),

  ROLLOUT_READ("rollout.read"),
  ROLLOUT_CREATE("rollout.create"),
  ROLLOUT_START("rollout.start"),
  ROLLOUT_ADVANCE("rollout.advance"),
  ROLLOUT_PAUSE("rollout.pause"),
  ROLLOUT_RESUME("rollout.resume"),
  ROLLOUT_CANCEL("rollout.cancel"),
  ROLLOUT_ROLLBACK("rollout.rollback"),

  SERVICE_READ("service.read"),
  SERVICE_MANAGE("service.manage"),
  SERVICE_INSTANCE_READ("service_instance.read"),
  SERVICE_INSTANCE_MANAGE("service_instance.manage"),

  EVENT_READ("event.read"),
  AUDIT_READ("audit.read"),

  WEBHOOK_READ("webhook.read"),
  WEBHOOK_MANAGE("webhook.manage"),
  WEBHOOK_DELIVERY_READ("webhook_delivery.read"),
  WEBHOOK_DELIVERY_REPLAY("webhook_delivery.replay"),

  CREDENTIAL_READ("credential.read"),
  CREDENTIAL_CREATE("credential.create"),
  CREDENTIAL_ROTATE("credential.rotate"),
  CREDENTIAL_REVOKE("credential.revoke"),

  SERVICE_ACCOUNT_READ("service_account.read"),
  SERVICE_ACCOUNT_MANAGE("service_account.manage");

  private static final Map<String, ManagementPermission> BY_NAME =
      Arrays.stream(values()).collect(Collectors.toMap(ManagementPermission::value, p -> p));

  private final String value;

  ManagementPermission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public String family() {
    int dot = value.indexOf('.');
    return dot < 0 ? value : value.substring(0, dot);
  }

  public static ManagementPermission parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Permission must not be blank");
    }
    ManagementPermission permission = BY_NAME.get(raw.trim());
    if (permission == null) {
      throw new IllegalArgumentException("Unknown permission: " + raw);
    }
    return permission;
  }

  public static Set<ManagementPermission> parseAll(Iterable<String> rawValues) {
    if (rawValues == null) {
      return Set.of();
    }
    return java.util.stream.StreamSupport.stream(rawValues.spliterator(), false)
        .map(ManagementPermission::parse)
        .collect(Collectors.toUnmodifiableSet());
  }
}
