package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  @Test
  void sameGraphProducesSameHash() {
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, now, now);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"POST", "GET"},
            poolId,
            true,
            now,
            now);

    HashableRuntimePayload first =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    HashableRuntimePayload second =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));

    String hash1 = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(first));
    String hash2 = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(second));
    assertEquals(hash1, hash2);
    assertFalse(first.toString().contains("RouteEntity"));
  }

  @Test
  void meaningfulChangeProducesDifferentHash() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID routeA = UUID.randomUUID();
    UUID routeB = UUID.randomUUID();

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, now, now);
    RouteEntity route1 =
        new RouteEntity(
            routeA,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            poolId,
            true,
            now,
            now);
    RouteEntity route2 =
        new RouteEntity(
            routeB,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET", "POST"},
            poolId,
            true,
            now,
            now);

    HashableRuntimePayload payload1 =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID,
            GATEWAY,
            List.of(route1),
            Map.of(poolId, pool),
            Map.of(poolId, List.of(target)));
    HashableRuntimePayload payload2 =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID,
            GATEWAY,
            List.of(route2),
            Map.of(poolId, pool),
            Map.of(poolId, List.of(target)));

    String hash1 = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload1));
    String hash2 = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload2));
    assertNotEquals(hash1, hash2);
  }

  @Test
  void hashIsNotSelfReferential() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID routeId = UUID.randomUUID();
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, now, now);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            poolId,
            true,
            now,
            now);
    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    StoredRuntimeSnapshot stored = RuntimeConfigCompiler.toStoredSnapshot(payload, 1L, hash);
    String storedJson = RuntimeContentHasher.canonicalJson(stored);
    assertTrue(storedJson.contains(hash));
    assertNotEquals(
        hash, RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(stored)));
  }

  @Test
  void deterministicMethodOrdering() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID routeId = UUID.randomUUID();
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, now, now);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"POST", "GET"},
            poolId,
            true,
            now,
            now);
    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    assertEquals(List.of("GET", "POST"), payload.routes().getFirst().methods());
  }
}
