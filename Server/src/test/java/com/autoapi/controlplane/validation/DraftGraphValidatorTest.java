package com.autoapi.controlplane.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.persistence.ApiEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DraftGraphValidatorTest {

  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

  @Test
  void rejectsNoEnabledRoutes() {
    UUID apiId = UUID.randomUUID();
    ApiEntity api = api(apiId, true);
    ValidationResult result =
        DraftGraphValidator.validate(api, List.of(), List.of(), List.of(), GATEWAY);
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> "NO_ENABLED_ROUTES".equals(e.code())));
  }

  @Test
  void rejectsInvalidPathPrefix() {
    UUID apiId = UUID.randomUUID();
    UUID poolId = UUID.randomUUID();
    ApiEntity api = api(apiId, true);
    UpstreamPoolEntity pool = pool(poolId, apiId);
    UpstreamTargetEntity target = target(poolId);
    RouteEntity route =
        new RouteEntity(
            UUID.randomUUID(),
            apiId,
            "bad",
            "api.local",
            "v1",
            new String[] {"GET"},
            poolId,
            true,
            NOW,
            NOW);
    ValidationResult result =
        DraftGraphValidator.validate(api, List.of(route), List.of(pool), List.of(target), GATEWAY);
    assertFalse(result.valid());
    assertTrue(
        result.errors().stream().anyMatch(e -> "ROUTE_PATH_PREFIX_INVALID".equals(e.code())));
  }

  @Test
  void rejectsUnsupportedLoadBalancing() {
    UUID apiId = UUID.randomUUID();
    UUID poolId = UUID.randomUUID();
    ApiEntity api = api(apiId, true);
    UpstreamPoolEntity pool = new UpstreamPoolEntity(poolId, apiId, "pool", "LEAST_CONN", NOW, NOW);
    UpstreamTargetEntity target = target(poolId);
    RouteEntity route = route(apiId, poolId, "/v1", "GET");
    ValidationResult result =
        DraftGraphValidator.validate(api, List.of(route), List.of(pool), List.of(target), GATEWAY);
    assertTrue(
        result.errors().stream().anyMatch(e -> "POOL_UNSUPPORTED_LOAD_BALANCING".equals(e.code())));
  }

  @Test
  void rejectsUpstreamNonRootPath() {
    UUID apiId = UUID.randomUUID();
    UUID poolId = UUID.randomUUID();
    ApiEntity api = api(apiId, true);
    UpstreamPoolEntity pool = pool(poolId, apiId);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(
            UUID.randomUUID(), poolId, "http://upstream:8080/v1", true, 1, NOW, NOW);
    RouteEntity route = route(apiId, poolId, "/v1/orders", "GET");
    ValidationResult result =
        DraftGraphValidator.validate(api, List.of(route), List.of(pool), List.of(target), GATEWAY);
    assertTrue(result.errors().stream().anyMatch(e -> "UPSTREAM_URL_INVALID".equals(e.code())));
  }

  @Test
  void rejectsRoutePoolFromAnotherApi() {
    UUID apiId = UUID.randomUUID();
    UUID otherApiId = UUID.randomUUID();
    UUID poolId = UUID.randomUUID();
    ApiEntity api = api(apiId, true);
    UpstreamPoolEntity pool = pool(poolId, otherApiId);
    UpstreamTargetEntity target = target(poolId);
    RouteEntity route = route(apiId, poolId, "/v1/orders", "GET");
    ValidationResult result =
        DraftGraphValidator.validate(api, List.of(route), List.of(pool), List.of(target), GATEWAY);
    assertTrue(result.errors().stream().anyMatch(e -> "ROUTE_POOL_WRONG_API".equals(e.code())));
  }

  private static ApiEntity api(UUID apiId, boolean enabled) {
    return new ApiEntity(
        apiId, UUID.randomUUID(), "orders-api", "api.local", "/", enabled, null, NOW, NOW);
  }

  private static UpstreamPoolEntity pool(UUID poolId, UUID apiId) {
    return new UpstreamPoolEntity(poolId, apiId, "orders-v1", "ROUND_ROBIN", NOW, NOW);
  }

  private static UpstreamTargetEntity target(UUID poolId) {
    return new UpstreamTargetEntity(
        UUID.randomUUID(), poolId, "http://upstream-v1:8080", true, 1, NOW, NOW);
  }

  private static RouteEntity route(UUID apiId, UUID poolId, String prefix, String method) {
    return new RouteEntity(
        UUID.randomUUID(),
        apiId,
        "orders-route",
        "api.local",
        prefix,
        new String[] {method},
        poolId,
        true,
        NOW,
        NOW);
  }
}
