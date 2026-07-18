package com.autoapi.middleware;

import com.autoapi.gateway.observability.RequestIdValidator;
import com.autoapi.proxy.GatewayAttributes;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class RequestIdFilter implements WebFilter {

  public static final String HEADER = "X-Request-ID";
  static final int MAX_REQUEST_ID_LENGTH = RequestIdValidator.MAX_REQUEST_ID_LENGTH;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = resolveRequestId(exchange.getRequest().getHeaders().get(HEADER));
    exchange.getAttributes().put(GatewayAttributes.REQUEST_ID, requestId);
    exchange.getResponse().getHeaders().set(HEADER, requestId);
    return chain.filter(exchange);
  }

  static String resolveRequestId(List<String> headerValues) {
    return RequestIdValidator.resolve(headerValues);
  }

  static String sanitize(String requestId) {
    if (requestId.length() <= MAX_REQUEST_ID_LENGTH) {
      return requestId;
    }
    return requestId.substring(0, MAX_REQUEST_ID_LENGTH);
  }
}
