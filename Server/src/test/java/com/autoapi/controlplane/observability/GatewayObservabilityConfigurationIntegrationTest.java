package com.autoapi.controlplane.observability;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.autoapi.gateway.observability.GatewayInstanceIdentity;
import com.autoapi.gateway.observability.GatewayObservabilityMetrics;
import com.autoapi.gateway.observability.GatewayRequestSummaryBuffer;
import com.autoapi.gateway.observability.GatewayStructuredLogger;
import com.autoapi.gateway.observability.GatewayTracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "autoapi.role=combined",
      "autoapi.gateway.observability.tracing-enabled=false",
      "autoapi.gateway.observability.otlp-endpoint="
    })
class GatewayObservabilityConfigurationIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired ApplicationContext context;

  @Test
  void observabilityBeansAvailableWithoutExternalCollector() {
    assertNotNull(context.getBean(GatewayTracer.class));
    assertNotNull(context.getBean(GatewayObservabilityMetrics.class));
    assertNotNull(context.getBean(GatewayStructuredLogger.class));
    assertNotNull(context.getBean(GatewayRequestSummaryBuffer.class));
    assertNotNull(context.getBean(GatewayInstanceIdentity.class));
  }
}
