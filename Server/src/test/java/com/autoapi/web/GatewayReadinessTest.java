package com.autoapi.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.controlplane.DatabaseReadinessChecker;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayReadinessTest {

  private RuntimeConfigHolder runtimeConfigHolder;
  private GatewayReadiness readiness;

  @BeforeEach
  void setUp() {
    runtimeConfigHolder =
        new RuntimeConfigHolder(
            new RuntimeConfig(
                new GatewayConfig("0.0.0.0", 8080),
                List.of(
                    new RouteConfig(
                        "orders",
                        "api.autoapi.local",
                        "/v1/orders",
                        Set.of(HttpMethod.GET),
                        new UpstreamConfig(URI.create("http://127.0.0.1:9080"))))));
    readiness = new GatewayReadiness(runtimeConfigHolder, Optional.empty());
  }

  @Test
  void isReadyReturnsFalseBeforeApplicationReadyEvent() {
    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void isReadyReturnsTrueWhenLocalComponentsReadyAndControlPlaneDisabled() {
    readiness.onReady();

    StepVerifier.create(readiness.isReady()).expectNext(true).verifyComplete();
  }

  @Test
  void isReadyReturnsTrueWhenDatabaseCheckSucceeds() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady()).thenReturn(Mono.just(true));
    readiness = new GatewayReadiness(runtimeConfigHolder, Optional.of(databaseReadinessChecker));
    readiness.onReady();

    StepVerifier.create(readiness.isReady()).expectNext(true).verifyComplete();
  }

  @Test
  void isReadyReturnsFalseWhenDatabaseCheckReturnsFalse() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady()).thenReturn(Mono.just(false));
    readiness = new GatewayReadiness(runtimeConfigHolder, Optional.of(databaseReadinessChecker));
    readiness.onReady();

    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void isReadyReturnsFalseWhenDatabasePublisherFails() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady())
        .thenReturn(Mono.error(new IllegalStateException("connection refused")));
    readiness = new GatewayReadiness(runtimeConfigHolder, Optional.of(databaseReadinessChecker));
    readiness.onReady();

    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void readinessImplementationDoesNotBlock() throws Exception {
    Path source = Path.of("src/main/java/com/autoapi/web/GatewayReadiness.java").toAbsolutePath();
    String contents = Files.readString(source);
    assertFalse(contents.contains("blockOptional("));
    assertFalse(contents.contains(".block("));
    assertFalse(contents.contains(".blockFirst("));
    assertFalse(contents.contains(".blockLast("));
  }
}
