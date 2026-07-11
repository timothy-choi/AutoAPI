package com.autoapi.middleware;

import com.autoapi.proxy.GatewayAttributes;
import org.springframework.stereotype.Component;

@Component
public class RequestIdSupport {

  public static final String HEADER = RequestIdFilter.HEADER;

  public static String getRequestId(org.springframework.web.server.ServerWebExchange exchange) {
    Object value = exchange.getAttribute(GatewayAttributes.REQUEST_ID);
    if (value instanceof String id && !id.isBlank()) {
      return id;
    }
    return java.util.UUID.randomUUID().toString();
  }
}
