package com.autoapi.gateway.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits structured JSON observability events without logging secrets. */
@Component
public class GatewayStructuredLogger {

  private static final Logger log = LoggerFactory.getLogger(GatewayStructuredLogger.class);

  private final ObjectMapper objectMapper;
  private final String gatewayId;

  public GatewayStructuredLogger(ObjectMapper objectMapper, String gatewayId) {
    this.objectMapper = objectMapper;
    this.gatewayId = gatewayId == null ? "unknown" : gatewayId;
  }

  public void requestCompleted(
      String requestId,
      GatewayObservabilityContext context,
      String method,
      int status,
      long durationMs,
      String signal) {
    Map<String, Object> payload = baseEvent("gateway_request_completed");
    payload.put("gatewayId", gatewayId);
    payload.put("requestId", requestId);
    payload.put("traceId", safe(context.traceId()));
    payload.put("spanId", safe(context.spanId()));
    payload.put("apiId", safe(context.apiId()));
    payload.put("routeId", safe(context.routeId()));
    payload.put("method", safe(method));
    payload.put("normalizedPath", safe(context.normalizedPath()));
    payload.put("status", status);
    payload.put("durationMs", durationMs);
    payload.put("snapshotVersion", safe(context.snapshotVersion()));
    payload.put("selectedPool", safe(context.selectedPool()));
    payload.put("selectedTargetId", safe(context.selectedTargetId()));
    payload.put("attemptCount", context.attemptCount());
    payload.put("retryCount", context.retryCount());
    payload.put("fallbackUsed", context.fallbackUsed());
    payload.put(
        "circuitBreakerState",
        context.circuitBreakerState() == null ? "" : context.circuitBreakerState().name());
    payload.put("healthState", context.healthState() == null ? "" : context.healthState());
    payload.put("bytesReceived", context.bytesReceived());
    payload.put("bytesSent", context.bytesSent());
    payload.put("clientDisconnected", context.clientDisconnected());
    payload.put("errorType", context.errorType().metricValue());
    payload.put("signal", safe(signal));
    emit(payload);
  }

  public void targetSelected(
      String requestId, String apiId, String routeId, String targetId, String pool, String reason) {
    Map<String, Object> payload = baseEvent("gateway_target_selected");
    payload.put("gatewayId", gatewayId);
    payload.put("requestId", requestId);
    payload.put("apiId", safe(apiId));
    payload.put("routeId", safe(routeId));
    payload.put("targetId", safe(targetId));
    payload.put("pool", safe(pool));
    payload.put("reason", safe(reason));
    emit(payload);
  }

  public void retryScheduled(
      String requestId, String routeId, int attemptNumber, GatewayErrorType reason) {
    Map<String, Object> payload = baseEvent("gateway_retry_scheduled");
    payload.put("gatewayId", gatewayId);
    payload.put("requestId", requestId);
    payload.put("routeId", safe(routeId));
    payload.put("attemptNumber", attemptNumber);
    payload.put("reason", reason.metricValue());
    emit(payload);
  }

  public void fallbackSelected(String requestId, String routeId, String reason) {
    Map<String, Object> payload = baseEvent("gateway_fallback_selected");
    payload.put("gatewayId", gatewayId);
    payload.put("requestId", requestId);
    payload.put("routeId", safe(routeId));
    payload.put("reason", safe(reason));
    emit(payload);
  }

  public void requestRejected(
      String requestId, String routeId, GatewayErrorType reason, String policyId) {
    Map<String, Object> payload = baseEvent("gateway_request_rejected");
    payload.put("gatewayId", gatewayId);
    payload.put("requestId", requestId);
    payload.put("routeId", safe(routeId));
    payload.put("reason", reason.metricValue());
    payload.put("policyId", safe(policyId));
    emit(payload);
  }

  public void runtimeSnapshotActivated(
      String instanceId,
      String previousSnapshotVersion,
      String newSnapshotVersion,
      long configurationVersion,
      long routeCount,
      long targetCount,
      long activationDurationMs) {
    Map<String, Object> payload = baseEvent("runtime_snapshot_activated");
    payload.put("gatewayId", gatewayId);
    payload.put("instanceId", safe(instanceId));
    payload.put("previousSnapshotVersion", safe(previousSnapshotVersion));
    payload.put("newSnapshotVersion", safe(newSnapshotVersion));
    payload.put("configurationVersion", configurationVersion);
    payload.put("routeCount", routeCount);
    payload.put("targetCount", targetCount);
    payload.put("activationDurationMs", activationDurationMs);
    emit(payload);
  }

  public void heartbeatFailed(String instanceId, String reason) {
    Map<String, Object> payload = baseEvent("gateway_heartbeat_failed");
    payload.put("gatewayId", gatewayId);
    payload.put("instanceId", safe(instanceId));
    payload.put("reason", safe(reason));
    emit(payload);
  }

  private Map<String, Object> baseEvent(String event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("timestamp", Instant.now().toString());
    payload.put("level", "INFO");
    payload.put("event", event);
    return payload;
  }

  private void emit(Map<String, Object> payload) {
    try {
      log.info(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      log.info("event={} gatewayId={} serializationFailed=true", payload.get("event"), gatewayId);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
