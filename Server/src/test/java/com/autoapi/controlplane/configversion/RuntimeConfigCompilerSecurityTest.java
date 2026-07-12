package com.autoapi.controlplane.configversion;

import static org.junit.jupiter.api.Assertions.*;

import com.autoapi.controlplane.persistence.ApiKeyEntity;
import com.autoapi.security.ApiKeyDigestService;
import com.autoapi.security.ApiKeyGenerator;
import com.autoapi.support.SecurityTestFixtures;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeConfigCompilerSecurityTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  @Test
  void snapshotIncludesDigestNotPlaintextOrPepper() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ApiKeyGenerator.GeneratedApiKeyMaterial material = ApiKeyGenerator.generate();
    byte[] digest =
        ApiKeyDigestService.digestSecret(material.secret(), SecurityTestFixtures.TEST_PEPPER);
    ApiKeyEntity key =
        new ApiKeyEntity(
            UUID.randomUUID(),
            API_ID,
            material.keyId(),
            "client",
            material.keyPrefix(),
            digest,
            true,
            null,
            now,
            now,
            null);

    List<CompiledApiKeySection> compiled = RuntimeConfigCompiler.compileApiKeys(List.of(key), now);
    assertEquals(1, compiled.size());
    assertEquals(HexFormat.of().formatHex(digest), compiled.getFirst().secretDigest());
    String json =
        RuntimeContentHasher.canonicalJson(
            new HashableRuntimePayload(API_ID, GATEWAY, List.of(), compiled));
    assertFalse(json.contains(material.plaintextKey()));
    assertFalse(json.contains(SecurityTestFixtures.TEST_PEPPER));
    assertFalse(json.contains(material.secret()));
  }

  @Test
  void revokedAndExpiredKeysExcluded() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ApiKeyGenerator.GeneratedApiKeyMaterial material = ApiKeyGenerator.generate();
    byte[] digest =
        ApiKeyDigestService.digestSecret(material.secret(), SecurityTestFixtures.TEST_PEPPER);
    ApiKeyEntity revoked =
        new ApiKeyEntity(
            UUID.randomUUID(),
            API_ID,
            material.keyId(),
            "revoked",
            material.keyPrefix(),
            digest,
            false,
            null,
            now,
            now,
            now);
    ApiKeyEntity expired =
        new ApiKeyEntity(
            UUID.randomUUID(),
            API_ID,
            "EXPIREDKEY123456",
            "expired",
            "ak_live_EXPI...1234",
            digest,
            true,
            now.minusDays(1),
            now,
            now,
            null);

    assertFalse(RuntimeConfigCompiler.isPublishableAt(revoked, now));
    assertFalse(
        RuntimeConfigCompiler.compileApiKeys(List.of(revoked), now).stream().findAny().isPresent());
    assertTrue(RuntimeConfigCompiler.compileApiKeys(List.of(expired), now).isEmpty());
  }
}
