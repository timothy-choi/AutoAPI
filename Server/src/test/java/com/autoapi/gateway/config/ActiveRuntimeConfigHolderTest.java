package com.autoapi.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.gateway.GatewayProperties;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ActiveRuntimeConfigHolderTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  private ActiveRuntimeConfigHolder holder;
  private LocalGatewayConfigActivator activator;

  @BeforeEach
  void setUp() {
    holder = new ActiveRuntimeConfigHolder();
    GatewayProperties gatewayProperties = new GatewayProperties();
    gatewayProperties.setApiId(API_ID);
    activator = new LocalGatewayConfigActivator(holder, gatewayProperties);
  }

  @Test
  void atomicActivationReplacesReference() {
    ActiveRuntimeBundle first = bundleWithVersion(1);
    ActiveRuntimeBundle second = bundleWithVersion(2);

    holder.activate(first);
    assertSame(first, holder.getActive());

    holder.activate(second);
    assertSame(second, holder.getActive());
    assertEquals(2, holder.getActive().version());
  }

  @Test
  void failedCandidateDoesNotReplaceActiveConfig() {
    ActiveRuntimeBundle active = bundleWithVersion(1);
    holder.activate(active);

    StoredRuntimeSnapshot invalid = snapshotWithTamperedHash(1);
    assertFalse(activator.activateCandidate(invalid).success());

    assertSame(active, holder.getActive());
    assertEquals(1, holder.getActive().version());
  }

  @Test
  void roundRobinSelectionRotates() {
    RouteConfig route =
        new RouteConfig(
            "orders",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET),
            UpstreamConfig.roundRobin(
                List.of(
                    URI.create("http://upstream-a:8080"), URI.create("http://upstream-b:8080"))));
    ActiveRuntimeBundle bundle =
        new ActiveRuntimeBundle(
            API_ID,
            1,
            "round-robin",
            new RuntimeConfig(new GatewayConfig("0.0.0.0", 8080), List.of(route)));
    holder.activate(bundle);

    ActiveRuntimeBundle active = holder.getActiveForRequest();
    assertEquals(URI.create("http://upstream-a:8080"), active.selectUpstream(route));
    assertEquals(URI.create("http://upstream-b:8080"), active.selectUpstream(route));
    assertEquals(URI.create("http://upstream-a:8080"), active.selectUpstream(route));
    assertTrue(holder.hasActiveConfig());
  }

  private static ActiveRuntimeBundle bundleWithVersion(long version) {
    StoredRuntimeSnapshot snapshot = validSnapshot(version);
    return new ActiveRuntimeBundle(
        snapshot.apiId(),
        snapshot.version(),
        snapshot.contentHash(),
        new RuntimeConfig(
            new GatewayConfig(snapshot.gateway().listenAddress(), snapshot.gateway().port()),
            List.of(
                new RouteConfig(
                    snapshot.routes().getFirst().id().toString(),
                    snapshot.routes().getFirst().host(),
                    snapshot.routes().getFirst().pathPrefix(),
                    Set.of(HttpMethod.GET),
                    UpstreamConfig.single(URI.create("http://upstream-v1:8080"))))));
  }

  private static StoredRuntimeSnapshot validSnapshot(long version) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", now, now);
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
        RuntimeConfigCompiler.compile(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }

  private static StoredRuntimeSnapshot snapshotWithTamperedHash(long version) {
    StoredRuntimeSnapshot snapshot = validSnapshot(version);
    return new StoredRuntimeSnapshot(
        snapshot.apiId(), snapshot.version(), "deadbeef", snapshot.gateway(), snapshot.routes());
  }
}
