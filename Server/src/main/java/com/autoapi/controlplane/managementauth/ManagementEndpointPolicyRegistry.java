package com.autoapi.controlplane.managementauth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ManagementEndpointPolicyRegistry {

  private final List<RegisteredPolicy> policies = new ArrayList<>();

  public ManagementEndpointPolicyRegistry() {
    registerProjectRoutes();
    registerApiRoutes();
    registerPolicyRoutes();
    registerPolicyEngineRoutes();
    registerConfigurationRoutes();
    registerGatewayRoutes();
    registerGatewayGroupRoutes();
    registerRolloutRoutes();
    registerWebhookRoutes();
    registerEventsRoutes();
    registerServiceDiscoveryRoutes();
    registerManagementAuthRoutes();
  }

  private void registerProjectRoutes() {
    register(HttpMethod.GET, "^/api/v1/projects$", ManagementPermission.PROJECT_READ, true);
    register(HttpMethod.POST, "^/api/v1/projects$", ManagementPermission.PROJECT_CREATE, true);
    register(HttpMethod.GET, "^/api/v1/projects/[^/]+$", ManagementPermission.PROJECT_READ, false);
    register(
        HttpMethod.POST, "^/api/v1/projects/[^/]+/apis$", ManagementPermission.API_MANAGE, false);
    register(HttpMethod.GET, "^/api/v1/projects/[^/]+/apis$", ManagementPermission.API_READ, false);
    register(
        HttpMethod.POST,
        "^/api/v1/projects/[^/]+/services$",
        ManagementPermission.SERVICE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/projects/[^/]+/services$",
        ManagementPermission.SERVICE_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/projects/[^/]+/services/[^/]+$",
        ManagementPermission.SERVICE_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/projects/[^/]+/services/[^/]+$",
        ManagementPermission.SERVICE_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/projects/[^/]+/services/[^/]+$",
        ManagementPermission.SERVICE_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/projects/[^/]+/services/[^/]+/registration-credentials$",
        ManagementPermission.SERVICE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/projects/[^/]+/services/[^/]+/registration-credentials$",
        ManagementPermission.SERVICE_READ,
        false);
  }

  private void registerApiRoutes() {
    register(HttpMethod.GET, "^/api/v1/apis/[^/]+$", ManagementPermission.API_READ, true);
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/upstream-pools$",
        ManagementPermission.API_MANAGE,
        true);
    register(
        HttpMethod.GET, "^/api/v1/apis/[^/]+/upstream-pools$", ManagementPermission.API_READ, true);
    register(
        HttpMethod.POST,
        "^/api/v1/upstream-pools/[^/]+/targets$",
        ManagementPermission.API_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/upstream-pools/[^/]+/targets$",
        ManagementPermission.API_READ,
        true);
    register(
        HttpMethod.POST, "^/api/v1/apis/[^/]+/routes$", ManagementPermission.ROUTE_MANAGE, true);
    register(HttpMethod.GET, "^/api/v1/apis/[^/]+/routes$", ManagementPermission.ROUTE_READ, true);
    register(
        HttpMethod.POST, "^/api/v1/apis/[^/]+/api-keys$", ManagementPermission.POLICY_MANAGE, true);
    register(
        HttpMethod.GET, "^/api/v1/apis/[^/]+/api-keys$", ManagementPermission.POLICY_READ, true);
    register(
        HttpMethod.GET,
        "^/api/v1/apis/[^/]+/api-keys/[^/]+$",
        ManagementPermission.POLICY_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/api-keys/[^/]+/revoke$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/apis/[^/]+/convergence$",
        ManagementPermission.GATEWAY_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/apis/[^/]+/activation-events$",
        ManagementPermission.CONFIGURATION_READ,
        true);
  }

  private void registerPolicyRoutes() {
    registerApiPolicyCrud(
        "rate-limit-policies",
        ManagementPermission.POLICY_READ,
        ManagementPermission.POLICY_MANAGE);
    registerApiPolicyCrud(
        "retry-policies", ManagementPermission.POLICY_READ, ManagementPermission.POLICY_MANAGE);
    registerApiPolicyCrud(
        "circuit-breaker-policies",
        ManagementPermission.POLICY_READ,
        ManagementPermission.POLICY_MANAGE);
    registerApiPolicyCrud(
        "traffic-split-policies",
        ManagementPermission.POLICY_READ,
        ManagementPermission.POLICY_MANAGE);
    registerApiPolicyCrud(
        "backend-health-policies",
        ManagementPermission.POLICY_READ,
        ManagementPermission.POLICY_MANAGE);

    register(
        HttpMethod.PUT,
        "^/api/v1/routes/[^/]+/policy-binding$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/routes/[^/]+/policy-binding$",
        ManagementPermission.POLICY_READ,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/routes/[^/]+/policy-binding$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PUT,
        "^/api/v1/routes/[^/]+/retry-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/routes/[^/]+/retry-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PUT,
        "^/api/v1/routes/[^/]+/circuit-breaker-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/routes/[^/]+/circuit-breaker-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PUT,
        "^/api/v1/routes/[^/]+/traffic-split-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/routes/[^/]+/traffic-split-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PUT,
        "^/api/v1/routes/[^/]+/discovered-service$",
        ManagementPermission.ROUTE_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/routes/[^/]+/discovered-service$",
        ManagementPermission.ROUTE_MANAGE,
        true);
    register(
        HttpMethod.PUT,
        "^/api/v1/upstream-pools/[^/]+/backend-health-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/upstream-pools/[^/]+/backend-health-policy$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/traffic-split-policies/[^/]+/destinations$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PATCH,
        "^/api/v1/traffic-split-policies/[^/]+/destinations/[^/]+$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/traffic-split-policies/[^/]+/destinations/[^/]+$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/traffic-split-policies/[^/]+/destinations$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.PATCH,
        "^/api/v1/apis/[^/]+/traffic-split-policies/[^/]+/destinations/[^/]+$",
        ManagementPermission.POLICY_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/apis/[^/]+/traffic-split-policies/[^/]+/destinations/[^/]+$",
        ManagementPermission.POLICY_MANAGE,
        true);
  }

  private void registerApiPolicyCrud(
      String collection,
      ManagementPermission readPermission,
      ManagementPermission managePermission) {
    register(HttpMethod.POST, "^/api/v1/apis/[^/]+/" + collection + "$", managePermission, true);
    register(HttpMethod.GET, "^/api/v1/apis/[^/]+/" + collection + "$", readPermission, true);
    register(HttpMethod.GET, "^/api/v1/apis/[^/]+/" + collection + "/[^/]+$", readPermission, true);
    register(
        HttpMethod.PATCH, "^/api/v1/apis/[^/]+/" + collection + "/[^/]+$", managePermission, true);
    register(
        HttpMethod.DELETE, "^/api/v1/apis/[^/]+/" + collection + "/[^/]+$", managePermission, true);
  }

  private void registerPolicyEngineRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/policy-bundles$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/policy-bundles$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+/revisions$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+/revisions$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_ASSIGN,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/organizations/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_DETACH,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_ASSIGN,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_DETACH,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_ASSIGN,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_DETACH,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/apis/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_ASSIGN,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/apis/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_DETACH,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/routes/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_ASSIGN,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/routes/[^/]+/policy-bundles/[^/]+/assignments$",
        ManagementPermission.POLICY_BUNDLE_DETACH,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/apis/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/apis/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/routes/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/routes/[^/]+/policy-overrides$",
        ManagementPermission.POLICY_BUNDLE_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/policy-overrides/[^/]+$",
        ManagementPermission.POLICY_BUNDLE_READ,
        true);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/policy-overrides/[^/]+$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        true);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/policy-overrides/[^/]+$",
        ManagementPermission.POLICY_BUNDLE_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/apis/[^/]+/effective-policy$",
        ManagementPermission.POLICY_EFFECTIVE_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/policies/evaluate$",
        ManagementPermission.POLICY_EVALUATE,
        true);
  }

  private void registerConfigurationRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/config/validate$",
        ManagementPermission.CONFIGURATION_CREATE,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/config/versions$",
        ManagementPermission.CONFIGURATION_CREATE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/apis/[^/]+/config/versions$",
        ManagementPermission.CONFIGURATION_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/apis/[^/]+/config/versions/[^/]+$",
        ManagementPermission.CONFIGURATION_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/apis/[^/]+/config/versions/[^/]+/activate$",
        ManagementPermission.CONFIGURATION_ACTIVATE,
        true);
  }

  private void registerGatewayRoutes() {
    register(HttpMethod.GET, "^/api/v1/gateways$", ManagementPermission.GATEWAY_READ, true);
    register(HttpMethod.GET, "^/api/v1/gateways/[^/]+$", ManagementPermission.GATEWAY_READ, true);
    register(
        HttpMethod.GET, "^/api/v1/management/gateways.*", ManagementPermission.GATEWAY_READ, true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/observability/.*",
        ManagementPermission.GATEWAY_READ,
        true);
  }

  private void registerGatewayGroupRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/gateway-groups$",
        ManagementPermission.GATEWAY_GROUP_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/gateway-groups$",
        ManagementPermission.GATEWAY_GROUP_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+$",
        ManagementPermission.GATEWAY_GROUP_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+$",
        ManagementPermission.GATEWAY_GROUP_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+$",
        ManagementPermission.GATEWAY_GROUP_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/gateways$",
        ManagementPermission.GATEWAY_GROUP_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/gateways/[^/]+$",
        ManagementPermission.GATEWAY_GROUP_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/gateways/[^/]+$",
        ManagementPermission.GATEWAY_GROUP_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/membership-preview$",
        ManagementPermission.GATEWAY_GROUP_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/gateway-groups/[^/]+/convergence$",
        ManagementPermission.GATEWAY_GROUP_READ,
        false);
  }

  private void registerRolloutRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts$",
        ManagementPermission.ROLLOUT_CREATE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/preview$",
        ManagementPermission.ROLLOUT_CREATE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/rollouts$",
        ManagementPermission.ROLLOUT_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+$",
        ManagementPermission.ROLLOUT_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/assignments$",
        ManagementPermission.ROLLOUT_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/preview$",
        ManagementPermission.ROLLOUT_CREATE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/start$",
        ManagementPermission.ROLLOUT_START,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/pause$",
        ManagementPermission.ROLLOUT_PAUSE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/resume$",
        ManagementPermission.ROLLOUT_RESUME,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/advance$",
        ManagementPermission.ROLLOUT_ADVANCE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/cancel$",
        ManagementPermission.ROLLOUT_CANCEL,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/rollouts/[^/]+/rollback$",
        ManagementPermission.ROLLOUT_ROLLBACK,
        false);
  }

  private void registerWebhookRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/webhooks$",
        ManagementPermission.WEBHOOK_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/webhooks$",
        ManagementPermission.WEBHOOK_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/webhooks/[^/]+$",
        ManagementPermission.WEBHOOK_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/projects/[^/]+/webhooks/[^/]+$",
        ManagementPermission.WEBHOOK_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/webhooks/[^/]+$",
        ManagementPermission.WEBHOOK_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/webhooks/[^/]+/rotate-secret$",
        ManagementPermission.WEBHOOK_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/webhooks/[^/]+/test$",
        ManagementPermission.WEBHOOK_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/webhook-deliveries$",
        ManagementPermission.WEBHOOK_DELIVERY_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/webhook-deliveries/[^/]+$",
        ManagementPermission.WEBHOOK_DELIVERY_READ,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/webhook-deliveries/[^/]+/replay$",
        ManagementPermission.WEBHOOK_DELIVERY_REPLAY,
        false);
  }

  private void registerEventsRoutes() {
    register(HttpMethod.GET, "^/api/v1/management/events$", ManagementPermission.EVENT_READ, true);
    register(
        HttpMethod.GET, "^/api/v1/management/events/[^/]+$", ManagementPermission.EVENT_READ, true);
    register(HttpMethod.GET, "^/api/v1/management/audit$", ManagementPermission.AUDIT_READ, true);
  }

  private void registerServiceDiscoveryRoutes() {
    register(
        HttpMethod.GET,
        "^/api/v1/services/[^/]+/instances$",
        ManagementPermission.SERVICE_INSTANCE_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/services/[^/]+/instances/[^/]+$",
        ManagementPermission.SERVICE_INSTANCE_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/services/[^/]+/instances/[^/]+/drain$",
        ManagementPermission.SERVICE_INSTANCE_MANAGE,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/services/[^/]+/instances/[^/]+/activate$",
        ManagementPermission.SERVICE_INSTANCE_MANAGE,
        true);
  }

  private void registerManagementAuthRoutes() {
    register(
        HttpMethod.POST,
        "^/api/v1/management/bootstrap$",
        ManagementPermission.ORGANIZATION_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/auth/me$",
        ManagementPermission.ORGANIZATION_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations$",
        ManagementPermission.ORGANIZATION_MANAGE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations$",
        ManagementPermission.ORGANIZATION_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+$",
        ManagementPermission.ORGANIZATION_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/organizations/[^/]+$",
        ManagementPermission.ORGANIZATION_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/role-bindings$",
        ManagementPermission.ORGANIZATION_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/role-bindings$",
        ManagementPermission.ORGANIZATION_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/organizations/[^/]+/role-bindings/[^/]+$",
        ManagementPermission.ORGANIZATION_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/projects/[^/]+/role-bindings$",
        ManagementPermission.PROJECT_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/projects/[^/]+/role-bindings$",
        ManagementPermission.PROJECT_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/projects/[^/]+/role-bindings/[^/]+$",
        ManagementPermission.PROJECT_MEMBERS_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/organizations/[^/]+/service-accounts$",
        ManagementPermission.SERVICE_ACCOUNT_MANAGE,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/service-accounts$",
        ManagementPermission.SERVICE_ACCOUNT_READ,
        false);
    register(
        HttpMethod.GET,
        "^/api/v1/management/organizations/[^/]+/service-accounts/[^/]+$",
        ManagementPermission.SERVICE_ACCOUNT_READ,
        false);
    register(
        HttpMethod.PATCH,
        "^/api/v1/management/organizations/[^/]+/service-accounts/[^/]+$",
        ManagementPermission.SERVICE_ACCOUNT_MANAGE,
        false);
    register(
        HttpMethod.DELETE,
        "^/api/v1/management/organizations/[^/]+/service-accounts/[^/]+$",
        ManagementPermission.SERVICE_ACCOUNT_MANAGE,
        false);
    register(
        HttpMethod.POST,
        "^/api/v1/management/service-accounts/[^/]+/credentials$",
        ManagementPermission.CREDENTIAL_CREATE,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/service-accounts/[^/]+/credentials$",
        ManagementPermission.CREDENTIAL_READ,
        true);
    register(
        HttpMethod.GET,
        "^/api/v1/management/service-accounts/[^/]+/credentials/[^/]+$",
        ManagementPermission.CREDENTIAL_READ,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/service-accounts/[^/]+/credentials/[^/]+/rotate$",
        ManagementPermission.CREDENTIAL_ROTATE,
        true);
    register(
        HttpMethod.POST,
        "^/api/v1/management/service-accounts/[^/]+/credentials/[^/]+/revoke$",
        ManagementPermission.CREDENTIAL_REVOKE,
        true);
  }

  private void register(
      HttpMethod method,
      String pattern,
      ManagementPermission permission,
      boolean organizationScope) {
    policies.add(
        new RegisteredPolicy(method, Pattern.compile(pattern), permission, organizationScope));
  }

  public EndpointPolicy resolve(HttpMethod method, String path) {
    for (RegisteredPolicy policy : policies) {
      if (policy.method.equals(method) && policy.pattern.matcher(path).matches()) {
        return new EndpointPolicy(policy.permission, policy.organizationScope);
      }
    }
    return null;
  }

  public List<RegisteredPolicy> registeredPolicies() {
    return List.copyOf(policies);
  }

  public record EndpointPolicy(ManagementPermission permission, boolean organizationScope) {}

  public record RegisteredPolicy(
      HttpMethod method,
      Pattern pattern,
      ManagementPermission permission,
      boolean organizationScope) {}
}
