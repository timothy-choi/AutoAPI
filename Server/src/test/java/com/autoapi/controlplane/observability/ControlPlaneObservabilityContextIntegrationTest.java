package com.autoapi.controlplane.observability;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.autoapi.controlplane.ControlPlaneIntegrationTest;
import com.autoapi.gateway.observability.GatewayObservabilityMetrics;
import com.autoapi.gateway.observability.GatewayRequestSummaryBuffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class ControlPlaneObservabilityContextIntegrationTest extends ControlPlaneIntegrationTest {

  @Autowired ApplicationContext context;

  @Test
  void gatewayObservabilityBeansAreNotRequiredForControlPlaneRole() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> context.getBean(GatewayRequestSummaryBuffer.class));
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> context.getBean(GatewayObservabilityMetrics.class));
  }
}
