package com.autoapi.gateway.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/** OpenTelemetry-backed tracer with no-op fallback when disabled or misconfigured. */
public final class OpenTelemetryGatewayTracer implements GatewayTracer {

  private static final Logger log = LoggerFactory.getLogger(OpenTelemetryGatewayTracer.class);
  private static final List<String> TRACE_HEADER_KEYS = List.of("traceparent", "tracestate");

  private static final TextMapGetter<HttpHeaders> HEADER_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpHeaders carrier) {
          return TRACE_HEADER_KEYS;
        }

        @Override
        public String get(HttpHeaders carrier, String key) {
          return carrier.getFirst(key);
        }
      };

  private static final TextMapSetter<HttpHeaders> HEADER_SETTER = HttpHeaders::set;

  private final boolean enabled;
  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;
  private final SdkTracerProvider tracerProvider;

  public OpenTelemetryGatewayTracer(GatewayObservabilityProperties properties, String gatewayId) {
    double sampleRate = clampSampleRate(properties.traceSampleRate());
    boolean otlpConfigured =
        properties.otlpEndpoint() != null && !properties.otlpEndpoint().isBlank();
    this.enabled = properties.tracingEnabled();
    if (!enabled) {
      this.openTelemetry = OpenTelemetry.noop();
      this.tracer = openTelemetry.getTracer("autoapi-gateway");
      this.tracerProvider = null;
      return;
    }
    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.builder()
                    .put("service.name", properties.serviceName())
                    .put("gateway.id", gatewayId == null ? "unknown" : gatewayId)
                    .build());
    var providerBuilder =
        SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.traceIdRatioBased(sampleRate));
    if (otlpConfigured) {
      try {
        OtlpGrpcSpanExporter exporter =
            OtlpGrpcSpanExporter.builder().setEndpoint(properties.otlpEndpoint()).build();
        providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
      } catch (RuntimeException ex) {
        log.warn(
            "Failed to configure OTLP exporter endpoint={} message={}",
            properties.otlpEndpoint(),
            ex.getMessage());
      }
    }
    this.tracerProvider = providerBuilder.build();
    this.openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    this.tracer = openTelemetry.getTracer("autoapi-gateway");
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public GatewayTraceScope startServerSpan(ServerWebExchange exchange, String spanName) {
    if (!enabled) {
      return NoopGatewayTraceScope.INSTANCE;
    }
    Context extracted =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), exchange.getRequest().getHeaders(), HEADER_GETTER);
    Span span =
        tracer.spanBuilder(spanName).setSpanKind(SpanKind.SERVER).setParent(extracted).startSpan();
    Scope scope = span.makeCurrent();
    return new OtelTraceScope(span, scope);
  }

  @Override
  public GatewayTraceScope startClientSpan(
      GatewayTraceScope parent, String spanName, String attemptId) {
    if (!enabled) {
      return NoopGatewayTraceScope.INSTANCE;
    }
    Span span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("attempt.id", attemptId)
            .startSpan();
    Scope scope = span.makeCurrent();
    return new OtelTraceScope(span, scope);
  }

  @Override
  public void inject(GatewayTraceScope scope, HttpHeaders headers) {
    if (!enabled || scope instanceof NoopGatewayTraceScope) {
      return;
    }
    Context context = Context.current();
    openTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, HEADER_SETTER);
  }

  @PreDestroy
  void shutdown() {
    if (tracerProvider != null) {
      tracerProvider.close();
    }
  }

  private static double clampSampleRate(double sampleRate) {
    if (Double.isNaN(sampleRate)) {
      return 1.0d;
    }
    return Math.max(0.0d, Math.min(1.0d, sampleRate));
  }

  private static final class OtelTraceScope implements GatewayTraceScope {

    private final Span span;
    private final Scope scope;

    private OtelTraceScope(Span span, Scope scope) {
      this.span = span;
      this.scope = scope;
    }

    @Override
    public String traceId() {
      return span.getSpanContext().getTraceId();
    }

    @Override
    public String spanId() {
      return span.getSpanContext().getSpanId();
    }

    @Override
    public void setAttribute(String key, String value) {
      if (value != null) {
        span.setAttribute(key, value);
      }
    }

    @Override
    public void setAttributes(Map<String, String> attributes) {
      if (attributes == null) {
        return;
      }
      attributes.forEach(
          (key, value) -> {
            if (value != null) {
              span.setAttribute(key, value);
            }
          });
    }

    @Override
    public void recordException(Throwable throwable) {
      if (throwable != null) {
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
      }
    }

    @Override
    public void close() {
      try {
        span.end();
      } finally {
        scope.close();
      }
    }
  }

  private static final class NoopGatewayTraceScope implements GatewayTraceScope {

    static final NoopGatewayTraceScope INSTANCE = new NoopGatewayTraceScope();

    private NoopGatewayTraceScope() {}

    @Override
    public String traceId() {
      return "";
    }

    @Override
    public String spanId() {
      return "";
    }

    @Override
    public void setAttribute(String key, String value) {}

    @Override
    public void setAttributes(Map<String, String> attributes) {}

    @Override
    public void recordException(Throwable throwable) {}

    @Override
    public void close() {}
  }
}
