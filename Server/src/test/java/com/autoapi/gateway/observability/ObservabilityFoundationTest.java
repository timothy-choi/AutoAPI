package com.autoapi.gateway.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.controlplane.observability.GatewayInstanceService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ObservabilityFoundationTest {

  @Test
  void requestIdValidatorAcceptsValidValues() {
    assertTrue(RequestIdValidator.isValid("req-abc-123"));
    assertEquals("req-abc-123", RequestIdValidator.resolve(java.util.List.of(" req-abc-123 ")));
  }

  @Test
  void requestIdValidatorRejectsMalformedValues() {
    assertFalse(RequestIdValidator.isValid("bad id with spaces"));
    String generated = RequestIdValidator.resolve(java.util.List.of("bad id with spaces"));
    assertTrue(generated.startsWith("req-"));
  }

  @Test
  void attemptIdIsStablePerAttemptNumber() {
    assertEquals("req-1-attempt-2", RequestIdValidator.attemptId("req-1", 2));
  }

  @Test
  void httpStatusClassBucketsResponses() {
    assertEquals("2xx", HttpStatusClass.fromStatusCode(200).label());
    assertEquals("5xx", HttpStatusClass.fromStatusCode(503).label());
    assertEquals("transport_error", HttpStatusClass.TRANSPORT_ERROR.label());
  }

  @Test
  void metricLabelsRejectRawPaths() {
    assertEquals("unknown", MetricLabelNormalizer.route("/v1/orders?secret=1"));
    assertEquals("get", MetricLabelNormalizer.method("GET"));
  }

  @Test
  void gatewayInstanceStatusDerivation() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleCutoff = now.minusSeconds(30);
    OffsetDateTime offlineCutoff = now.minusSeconds(120);
    assertEquals(
        "READY",
        GatewayInstanceService.deriveOperationalStatus(
            now.minusSeconds(5), staleCutoff, offlineCutoff));
    assertEquals(
        "STALE",
        GatewayInstanceService.deriveOperationalStatus(
            now.minusSeconds(60), staleCutoff, offlineCutoff));
    assertEquals(
        "OFFLINE",
        GatewayInstanceService.deriveOperationalStatus(
            now.minusSeconds(200), staleCutoff, offlineCutoff));
  }
}
