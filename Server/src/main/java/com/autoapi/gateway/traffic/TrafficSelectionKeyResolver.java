package com.autoapi.gateway.traffic;

import com.autoapi.config.RuntimeTrafficSplitConfig;
import com.autoapi.gateway.auth.AuthenticatedApiKey;
import com.autoapi.middleware.RequestIdSupport;
import com.autoapi.proxy.GatewayAttributes;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

public final class TrafficSelectionKeyResolver {

  public enum SelectionKeySource {
    API_KEY_ID,
    REQUEST_ID,
    HEADER,
    COOKIE
  }

  private TrafficSelectionKeyResolver() {}

  public static ResolvedSelectionKey resolve(
      RuntimeTrafficSplitConfig config, ServerWebExchange exchange) {
    return switch (config.selectionKey()) {
      case "API_KEY_ID" -> resolveApiKeyId(exchange);
      case "HEADER" -> resolveHeader(config.selectionKeyName(), exchange);
      case "COOKIE" -> resolveCookie(config.selectionKeyName(), exchange);
      default -> resolveRequestId(exchange);
    };
  }

  private static ResolvedSelectionKey resolveApiKeyId(ServerWebExchange exchange) {
    AuthenticatedApiKey apiKey = exchange.getAttribute(GatewayAttributes.AUTHENTICATED_API_KEY);
    if (apiKey != null && apiKey.keyId() != null && !apiKey.keyId().isBlank()) {
      return new ResolvedSelectionKey(apiKey.keyId(), SelectionKeySource.API_KEY_ID);
    }
    return resolveRequestId(exchange);
  }

  private static ResolvedSelectionKey resolveHeader(String headerName, ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    String value = request.getHeaders().getFirst(headerName);
    if (value != null && !value.isBlank()) {
      return new ResolvedSelectionKey(value.trim(), SelectionKeySource.HEADER);
    }
    return resolveRequestId(exchange);
  }

  private static ResolvedSelectionKey resolveCookie(String cookieName, ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest();
    if (request.getCookies().getFirst(cookieName) != null) {
      String value = request.getCookies().getFirst(cookieName).getValue();
      if (value != null && !value.isBlank()) {
        return new ResolvedSelectionKey(value.trim(), SelectionKeySource.COOKIE);
      }
    }
    return resolveRequestId(exchange);
  }

  private static ResolvedSelectionKey resolveRequestId(ServerWebExchange exchange) {
    String requestId = RequestIdSupport.getRequestId(exchange);
    return new ResolvedSelectionKey(requestId, SelectionKeySource.REQUEST_ID);
  }

  public record ResolvedSelectionKey(String value, SelectionKeySource source) {}
}
