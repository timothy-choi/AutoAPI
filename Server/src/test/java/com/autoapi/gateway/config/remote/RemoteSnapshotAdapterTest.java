package com.autoapi.gateway.config.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RouteEntity;
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
            snapshot.routes());

    RemoteSnapshotValidationException ex =
        assertThrows(
            RemoteSnapshotValidationException.class,
            () -> RemoteSnapshotAdapter.toActiveBundle(tampered, API_ID));
    assertEquals("Snapshot content hash mismatch", ex.getMessage());
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
            new String[] {"POST", "GET"},
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
}
