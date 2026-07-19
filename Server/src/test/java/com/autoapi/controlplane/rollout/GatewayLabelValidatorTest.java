package com.autoapi.controlplane.rollout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayLabelValidatorTest {

  private static final RolloutsProperties.Labels LIMITS =
      new RolloutsProperties.Labels(32, 64, 128);

  @Test
  void emptyLabelsReturnEmptyMap() {
    assertEquals(Map.of(), GatewayLabelValidator.validateLabels(null, LIMITS));
    assertEquals(Map.of(), GatewayLabelValidator.validateLabels(Map.of(), LIMITS));
  }

  @Test
  void validatesAndNormalizesLabels() {
    Map<String, String> labels =
        Map.of(
            "region", "us-west",
            "environment", "production");

    Map<String, String> validated = GatewayLabelValidator.validateLabels(labels, LIMITS);

    assertEquals(labels, validated);
  }

  @Test
  void rejectsBlankLabelKeyOrValue() {
    assertThrows(
        ControlPlaneException.class,
        () -> GatewayLabelValidator.validateLabels(Map.of(" ", "value"), LIMITS));
    assertThrows(
        ControlPlaneException.class,
        () -> GatewayLabelValidator.validateLabels(Map.of("region", " "), LIMITS));
  }

  @Test
  void rejectsSystemLabelNamespace() {
    assertThrows(
        ControlPlaneException.class,
        () -> GatewayLabelValidator.validateLabels(Map.of("autoapi.", "v1"), LIMITS));
  }

  @Test
  void rejectsSecretLikeLabels() {
    assertThrows(
        ControlPlaneException.class,
        () -> GatewayLabelValidator.validateLabels(Map.of("api-token", "value"), LIMITS));
    assertThrows(
        ControlPlaneException.class,
        () -> GatewayLabelValidator.validateLabels(Map.of("key", "ak_live_abc"), LIMITS));
  }

  @Test
  void rejectsTooManyLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    for (int i = 0; i < 33; i++) {
      labels.put("key" + i, "value" + i);
    }
    assertThrows(
        ControlPlaneException.class, () -> GatewayLabelValidator.validateLabels(labels, LIMITS));
  }

  @Test
  void mergeGatewayLabelsPrefersAdminAndSkipsGatewaySystemLabels() {
    Map<String, String> admin = Map.of("region", "us-west", "tier", "gold");
    Map<String, String> gateway =
        Map.of(
            "environment", "production",
            "autoapi.", "ignored",
            "region", "eu-central");

    Map<String, String> merged = GatewayLabelValidator.mergeGatewayLabels(admin, gateway);

    assertEquals("us-west", merged.get("region"));
    assertEquals("gold", merged.get("tier"));
    assertEquals("production", merged.get("environment"));
    assertTrue(merged.values().stream().noneMatch("ignored"::equals));
  }
}
