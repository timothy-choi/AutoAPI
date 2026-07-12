package com.autoapi.gateway.health;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import reactor.netty.http.client.PrematureCloseException;

class FailureClassifierTest {

  private FailureClassifier classifier;

  @BeforeEach
  void setUp() {
    classifier = new FailureClassifier();
  }

  @Test
  void classifiesQualifyingTransportFailures() {
    assertEquals(
        FailureCategory.DNS_FAILURE,
        classifier.classifyTransportFailure(new UnknownHostException("host")).orElseThrow());
    assertEquals(
        FailureCategory.CONNECTION_REFUSED,
        classifier.classifyTransportFailure(new ConnectException("refused")).orElseThrow());
    assertEquals(
        FailureCategory.CONNECTION_REFUSED,
        classifier.classifyTransportFailure(new ConnectTimeoutException("timeout")).orElseThrow());
    assertEquals(
        FailureCategory.CONNECTION_TIMEOUT,
        classifier
            .classifyTransportFailure(new java.net.SocketTimeoutException("read timed out"))
            .orElseThrow());
    assertEquals(
        FailureCategory.RESPONSE_TIMEOUT,
        classifier.classifyTransportFailure(new ReadTimeoutException()).orElseThrow());
    assertEquals(
        FailureCategory.PREMATURE_UPSTREAM_CLOSE,
        classifier
            .classifyTransportFailure(Mockito.mock(PrematureCloseException.class))
            .orElseThrow());
    assertEquals(
        FailureCategory.CONNECTION_RESET,
        classifier.classifyTransportFailure(new ClosedChannelException()).orElseThrow());
    assertEquals(
        FailureCategory.CONNECTION_RESET,
        classifier
            .classifyTransportFailure(new SocketException("Connection reset by peer"))
            .orElseThrow());
  }

  @Test
  void classifiesNestedCause() {
    RuntimeException wrapped =
        new RuntimeException("proxy failed", new ConnectException("connection refused"));
    assertEquals(
        FailureCategory.CONNECTION_REFUSED,
        classifier.classifyTransportFailure(wrapped).orElseThrow());
  }

  @Test
  void ignoresNonQualifyingFailures() {
    assertTrue(classifier.classifyTransportFailure(null).isEmpty());
    assertTrue(classifier.classifyTransportFailure(new IllegalStateException("boom")).isEmpty());
    assertTrue(classifier.classifyTransportFailure(new CancellationException()).isEmpty());
    assertTrue(
        classifier.classifyTransportFailure(new SocketException("network unreachable")).isEmpty());
  }

  @Test
  void ignoresHttpStatusResponses() {
    assertTrue(classifier.classifyHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR).isEmpty());
    assertTrue(classifier.classifyHttpStatus(HttpStatus.BAD_GATEWAY).isEmpty());
    assertTrue(classifier.classifyHttpStatus(HttpStatus.OK).isEmpty());
  }
}
