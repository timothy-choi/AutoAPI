package com.autoapi.controlplane.managementauth;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ManagementEndpointPolicyInventoryTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String PROJECT = "/api/v1/projects/" + ID;
  private static final String API = "/api/v1/apis/" + ID;
  private static final String MGMT_PROJECT = "/api/v1/management/projects/" + ID;

  private final ManagementEndpointPolicyRegistry registry = new ManagementEndpointPolicyRegistry();

  @Test
  void registeredPoliciesCoverAllManagementRoutes() {
    List<RouteSample> samples =
        List.of(
            route(HttpMethod.GET, "/api/v1/projects"),
            route(HttpMethod.POST, "/api/v1/projects"),
            route(HttpMethod.GET, PROJECT),
            route(HttpMethod.POST, PROJECT + "/apis"),
            route(HttpMethod.GET, API),
            route(HttpMethod.POST, API + "/upstream-pools"),
            route(HttpMethod.POST, "/api/v1/upstream-pools/" + ID + "/targets"),
            route(HttpMethod.POST, API + "/routes"),
            route(HttpMethod.POST, API + "/config/validate"),
            route(HttpMethod.POST, API + "/config/versions/" + ID + "/activate"),
            route(HttpMethod.POST, API + "/api-keys"),
            route(HttpMethod.GET, API + "/api-keys/" + ID),
            route(HttpMethod.POST, API + "/rate-limit-policies"),
            route(HttpMethod.PUT, "/api/v1/routes/" + ID + "/policy-binding"),
            route(HttpMethod.POST, API + "/retry-policies"),
            route(HttpMethod.PUT, "/api/v1/routes/" + ID + "/retry-policy"),
            route(HttpMethod.POST, API + "/circuit-breaker-policies"),
            route(HttpMethod.POST, API + "/traffic-split-policies"),
            route(HttpMethod.POST, API + "/backend-health-policies"),
            route(HttpMethod.PUT, "/api/v1/upstream-pools/" + ID + "/backend-health-policy"),
            route(HttpMethod.GET, "/api/v1/gateways"),
            route(HttpMethod.GET, "/api/v1/apis/" + ID + "/convergence"),
            route(HttpMethod.POST, MGMT_PROJECT + "/gateway-groups"),
            route(HttpMethod.POST, MGMT_PROJECT + "/rollouts/" + ID + "/start"),
            route(HttpMethod.POST, MGMT_PROJECT + "/webhooks"),
            route(HttpMethod.GET, MGMT_PROJECT + "/webhook-deliveries"),
            route(HttpMethod.POST, MGMT_PROJECT + "/webhook-deliveries/" + ID + "/replay"),
            route(HttpMethod.GET, "/api/v1/management/events"),
            route(HttpMethod.GET, "/api/v1/management/audit"),
            route(HttpMethod.POST, PROJECT + "/services"),
            route(HttpMethod.GET, "/api/v1/services/" + ID + "/instances"),
            route(HttpMethod.POST, "/api/v1/management/bootstrap"),
            route(HttpMethod.GET, "/api/v1/management/auth/me"),
            route(HttpMethod.POST, "/api/v1/management/organizations"),
            route(
                HttpMethod.DELETE,
                "/api/v1/management/organizations/" + ID + "/role-bindings/" + ID),
            route(HttpMethod.DELETE, "/api/v1/management/projects/" + ID + "/role-bindings/" + ID),
            route(
                HttpMethod.GET, "/api/v1/management/service-accounts/" + ID + "/credentials/" + ID),
            route(HttpMethod.GET, "/api/v1/management/organizations/" + ID + "/policy-bundles"),
            route(HttpMethod.POST, "/api/v1/management/organizations/" + ID + "/policy-bundles"),
            route(
                HttpMethod.POST,
                "/api/v1/management/organizations/" + ID + "/policy-bundles/" + ID + "/revisions"),
            route(
                HttpMethod.POST,
                "/api/v1/management/organizations/"
                    + ID
                    + "/policy-bundles/"
                    + ID
                    + "/assignments"),
            route(
                HttpMethod.POST,
                "/api/v1/management/projects/" + ID + "/policy-bundles/" + ID + "/assignments"),
            route(HttpMethod.POST, "/api/v1/management/apis/" + ID + "/policy-overrides"),
            route(HttpMethod.GET, "/api/v1/management/apis/" + ID + "/effective-policy"),
            route(HttpMethod.POST, "/api/v1/management/policies/evaluate"));

    for (RouteSample sample : samples) {
      assertNotNull(
          registry.resolve(sample.method(), sample.path()),
          () -> "Missing policy for " + sample.method() + " " + sample.path());
    }
  }

  private static RouteSample route(HttpMethod method, String path) {
    return new RouteSample(method, path);
  }

  private record RouteSample(HttpMethod method, String path) {}
}
