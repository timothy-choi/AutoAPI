package com.autoapi.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ErrorResponseWriterTest {

  @Test
  void serializationFallbackIsValidJsonWithoutClientControlledRequestId() throws Exception {
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    when(failingMapper.writeValueAsBytes(any()))
        .thenThrow(new JsonProcessingException("serialization failed") {});

    ErrorResponseWriter writer = new ErrorResponseWriter(failingMapper);
    byte[] bytes =
        writer.serializeErrorBody(
            "INTERNAL_GATEWAY_ERROR",
            "An internal gateway error occurred",
            "malicious\"},\"code\":\"FORGED");

    assertArrayEquals(ErrorResponseWriter.SERIALIZATION_FALLBACK_JSON, bytes);
    ObjectMapper verifier = new ObjectMapper();
    var tree = verifier.readTree(bytes);
    assertEquals("INTERNAL_GATEWAY_ERROR", tree.path("error").path("code").asText());
    assertEquals("unavailable", tree.path("error").path("requestId").asText());
  }

  @Test
  void internalErrorDoesNotExposeCauseInClientBody() {
    ErrorResponseWriter writer = new ErrorResponseWriter(new ObjectMapper());
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/v1/orders/1").build());

    writer
        .internalError(exchange, new RuntimeException("secret InternalDnsFailure at evil-host"))
        .block();

    var bodyBuffer =
        org.springframework.core.io.buffer.DataBufferUtils.join(exchange.getResponse().getBody())
            .block();
    assertNotNull(bodyBuffer);
    byte[] bytes = new byte[bodyBuffer.readableByteCount()];
    bodyBuffer.read(bytes);
    org.springframework.core.io.buffer.DataBufferUtils.release(bodyBuffer);
    String response = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(response.contains("INTERNAL_GATEWAY_ERROR"));
    assertFalse(response.contains("RuntimeException"));
    assertFalse(response.contains("secret InternalDnsFailure"));
    assertFalse(response.contains("evil-host"));
  }
}
