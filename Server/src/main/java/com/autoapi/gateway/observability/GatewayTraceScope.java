package com.autoapi.gateway.observability;

import java.util.Map;

/** Active trace span scope for gateway request and upstream attempt instrumentation. */
public interface GatewayTraceScope extends AutoCloseable {

  String traceId();

  String spanId();

  void setAttribute(String key, String value);

  void setAttributes(Map<String, String> attributes);

  void recordException(Throwable throwable);

  @Override
  void close();
}
