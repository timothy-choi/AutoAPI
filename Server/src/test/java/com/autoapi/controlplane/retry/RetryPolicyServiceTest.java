package com.autoapi.controlplane.retry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RetryPolicyServiceTest {

  @Test
  void validatesMaxAttemptsBounds() {
    assertThrows(
        com.autoapi.controlplane.api.ControlPlaneException.class,
        () ->
            RetryPolicyService.validateFields(
                0, 1000, true, true, true, true, new String[] {"GET"}, false, 20, 2, 10));
    assertThrows(
        com.autoapi.controlplane.api.ControlPlaneException.class,
        () ->
            RetryPolicyService.validateFields(
                6, 1000, true, true, true, true, new String[] {"GET"}, false, 20, 2, 10));
  }

  @Test
  void rejectsPostWithoutIdempotencyRequirement() {
    assertThrows(
        com.autoapi.controlplane.api.ControlPlaneException.class,
        () ->
            RetryPolicyService.validateFields(
                2, 1000, true, true, true, true, new String[] {"POST"}, false, 20, 2, 10));
  }

  @Test
  void allowsPostWithIdempotencyRequirement() {
    assertDoesNotThrow(
        () ->
            RetryPolicyService.validateFields(
                2, 1000, true, true, true, true, new String[] {"POST"}, true, 20, 2, 10));
  }

  @Test
  void normalizesMethodsDeterministically() {
    String[] normalized = RetryPolicyService.normalizeMethods(new String[] {"post", "GET", "HEAD"});
    assertArrayEquals(new String[] {"GET", "HEAD", "POST"}, normalized);
  }
}
