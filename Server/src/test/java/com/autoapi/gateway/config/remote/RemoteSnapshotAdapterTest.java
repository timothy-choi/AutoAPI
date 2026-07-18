package com.autoapi.gateway.config.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RetryPolicyEntity;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RoutePolicyBindingEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoteSnapshotAdapterTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  @Test
  void compilesValidSnapshotFromCompilerFixtures() {
    StoredRuntimeSnapshot snapshot = validSnapshot(1);

    ActiveRuntimeBundle bundle = RemoteSnapshotAdapter.toActiveBundle(snapshot, API_ID);

    assertEquals(API_ID, bundle.apiId());
    assertEquals(1, bundle.version());
    assertEquals(snapshot.contentHash(), bundle.contentHash());
    assertEquals("/v1/orders", bundle.runtimeConfig().routes().getFirst().pathPrefix());
    assertEquals("api.autoapi.local", bundle.runtimeConfig().routes().getFirst().host());
    assertEquals(
        "http://upstream-v1:8080",
        bundle.runtimeConfig().routes().getFirst().upstream().url().toString());
  }

  @Test
  void hashMismatchRejected() {
    StoredRuntimeSnapshot snapshot = validSnapshot(1);
    StoredRuntimeSnapshot tampered =
        new StoredRuntimeSnapshot(
            snapshot.apiId(),
            snapshot.version(),
            "deadbeef",
            snapshot.gateway(),
            snapshot.routes(),
            snapshot.apiKeys());

    RemoteSnapshotValidationException ex =
        assertThrows(
            RemoteSnapshotValidationException.class,
            () -> RemoteSnapshotAdapter.toActiveBundle(tampered, API_ID));
    assertEquals("Snapshot content hash mismatch", ex.getMessage());
  }

  @Test
  void activatesRuntimeSnapshotWithRetryPolicyAndTwoTargets() {
    StoredRuntimeSnapshot snapshot = retrySnapshot(1);

    ActiveRuntimeBundle bundle = RemoteSnapshotAdapter.toActiveBundle(snapshot, API_ID);

    var route = bundle.runtimeConfig().routes().getFirst();
    assertEquals(2, route.retry().maxAttempts());
    assertEquals(2, route.upstream().targets().size());
    assertEquals(true, route.retry().retryOnConnectFailure());
    assertEquals(List.of("GET", "HEAD"), route.retry().retryableMethods());
  }

  @Test
  void snapshotWithoutRetryPolicyHasNoRetrySection() {
    StoredRuntimeSnapshot snapshot = validSnapshot(1);

    ActiveRuntimeBundle bundle = RemoteSnapshotAdapter.toActiveBundle(snapshot, API_ID);

    assertEquals(null, bundle.runtimeConfig().routes().getFirst().retry());
    assertEquals(false, bundle.runtimeConfig().routes().getFirst().retryEnabled());
  }

  private static StoredRuntimeSnapshot retrySnapshot(long version) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetV1 = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID targetV2 = UUID.fromString("00000000-0000-0000-0000-000000000031");
    UUID policyId = UUID.fromString("00000000-0000-0000-0000-000000000040");

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target1 =
        new UpstreamTargetEntity(targetV1, poolId, "http://upstream-v1:8080", true, 1, now, now);
    UpstreamTargetEntity target2 =
        new UpstreamTargetEntity(targetV2, poolId, "http://upstream-v2:8080", true, 1, now, now);
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
    RetryPolicyEntity policy =
        new RetryPolicyEntity(
            policyId,
            API_ID,
            "retry",
            2,
            1000,
            true,
            true,
            true,
            true,
            new String[] {"HEAD", "GET"},
            true,
            50,
            2,
            10,
            true,
            now,
            now);
    RoutePolicyBindingEntity binding =
        new RoutePolicyBindingEntity(routeId, false, null, now, now, policyId, null);

    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compile(
            API_ID,
            GATEWAY,
            List.of(route),
            Map.of(poolId, pool),
            Map.of(poolId, List.of(target1, target2)),
            Map.of(routeId, binding),
            Map.of(),
            Map.of(),
            Map.of(policyId, policy),
            Map.of(),
            Map.of(),
            List.of(),
            now);
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }

  private static StoredRuntimeSnapshot validSnapshot(long version) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");

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
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }
}
