package com.autoapi.controlplane.policy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** OpenTelemetry spans for policy evaluation phases. */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyEngineTracer {

  private final Tracer tracer;

  public PolicyEngineTracer() {
    this.tracer = OpenTelemetry.noop().getTracer("autoapi-policy-engine");
  }

  public PolicySpanScope startPhase(String phaseName) {
    Span span = tracer.spanBuilder("policy." + phaseName).startSpan();
    return new PolicySpanScope(span);
  }

  public static final class PolicySpanScope implements AutoCloseable {
    private final Span span;

    PolicySpanScope(Span span) {
      this.span = span;
    }

    public Span span() {
      return span;
    }

    @Override
    public void close() {
      span.end();
    }
  }
}
