package com.autoapi.controlplane.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.CompiledObservabilityMetadataSection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerObservabilityTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  @Test
  void storedSnapshotIncludesObservabilityMetadata() {
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000011");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000012");
    OffsetDateTime compiledAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(
            poolId, API_ID, "primary", "ROUND_ROBIN", null, compiledAt, compiledAt);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(
            targetId, poolId, "http://127.0.0.1:9001", true, 1, compiledAt, compiledAt);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders",
            "api.autoapi.local",
            "/",
            new String[] {"GET"},
            poolId,
            true,
            compiledAt,
            compiledAt);
    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    StoredRuntimeSnapshot snapshot =
        RuntimeConfigCompiler.toStoredSnapshot(payload, 1L, hash, compiledAt);
    CompiledObservabilityMetadataSection metadata = snapshot.observabilityMetadata();
    assertNotNull(metadata);
    assertEquals(1, metadata.routeCount());
    assertEquals(1, metadata.targetCount());
    assertEquals(1L, metadata.configurationVersion());
  }
}
