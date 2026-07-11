package com.autoapi.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class ProxyHandlerTest {

  @Test
  void normalizedClientHostStripsPort() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/v1/orders/1")
            .header(HttpHeaders.HOST, "API.AUTOAPI.LOCAL:8443")
            .build();
    assertEquals("api.autoapi.local", ProxyHandler.normalizedClientHost(request));
  }

  @Test
  void normalizedClientHostDefaultsWhenMissing() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/v1/orders/1").build();
    assertEquals("localhost", ProxyHandler.normalizedClientHost(request));
  }
}
