package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.events.EventContext;
import com.autoapi.controlplane.events.PlatformEventRecorder;
import com.autoapi.controlplane.events.PlatformEventTypes;
import com.autoapi.controlplane.events.RecordPlatformEventRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyEvaluationService {

  private final EffectivePolicyService effectivePolicyService;
  private final PlatformEventRecorder eventRecorder;
  private final PolicyEngineMetrics metrics;
  private final PolicyEngineTracer tracer;

  public PolicyEvaluationService(
      EffectivePolicyService effectivePolicyService,
      PlatformEventRecorder eventRecorder,
      PolicyEngineMetrics metrics,
      PolicyEngineTracer tracer) {
    this.effectivePolicyService = effectivePolicyService;
    this.eventRecorder = eventRecorder;
    this.metrics = metrics;
    this.tracer = tracer;
  }

  public Mono<PolicyEvaluationResult> evaluate(
      PolicyEvaluationRequest request, EventContext context) {
    metrics.recordEvaluate();
    try (PolicyEngineTracer.PolicySpanScope span = tracer.startPhase("evaluate")) {
      Mono<EffectivePolicyDocument> documentMono;
      if (request.routeId() != null) {
        documentMono =
            effectivePolicyService.evaluateRoute(
                request.apiId(), request.routeId(), request.explain());
      } else {
        documentMono = effectivePolicyService.evaluateApi(request.apiId(), request.explain());
      }
      return documentMono.flatMap(
          document ->
              recordEvaluatedEvent(request, context)
                  .thenReturn(new PolicyEvaluationResult(request, document)));
    }
  }

  private Mono<Void> recordEvaluatedEvent(PolicyEvaluationRequest request, EventContext context) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("apiId", request.apiId().toString());
    if (request.routeId() != null) {
      payload.put("routeId", request.routeId().toString());
    }
    payload.put("explain", request.explain());
    return eventRecorder
        .record(
            RecordPlatformEventRequest.of(
                PlatformEventTypes.POLICY_EVALUATED,
                null,
                request.apiId(),
                "API",
                request.apiId().toString(),
                context,
                payload))
        .then();
  }

  public record PolicyEvaluationResult(
      PolicyEvaluationRequest request, EffectivePolicyDocument document) {}
}
