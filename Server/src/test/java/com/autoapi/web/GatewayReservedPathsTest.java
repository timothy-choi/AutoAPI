package com.autoapi.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GatewayReservedPathsTest {

  @Test
  void reservesOperationalAndManagementPaths() {
    assertTrue(GatewayReservedPaths.isReservedPath("/healthz"));
    assertTrue(GatewayReservedPaths.isReservedPath("/readyz"));
    assertTrue(GatewayReservedPaths.isReservedPath("/api/v1"));
    assertTrue(GatewayReservedPaths.isReservedPath("/api/v1/"));
    assertTrue(GatewayReservedPaths.isReservedPath("/api/v1/projects"));
    assertTrue(GatewayReservedPaths.isReservedPath("/api/v1/apis/123"));
  }

  @Test
  void doesNotReserveSimilarButDistinctPaths() {
    assertFalse(GatewayReservedPaths.isReservedPath("/api/v10/projects"));
    assertFalse(GatewayReservedPaths.isReservedPath("/api/v10/example"));
    assertFalse(GatewayReservedPaths.isReservedPath("/api/v1example"));
    assertFalse(GatewayReservedPaths.isReservedPath("/v1/orders/123"));
    assertFalse(GatewayReservedPaths.isReservedPath("/unknown"));
  }
}
