package com.autoapi.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.RuntimeConfigHolder;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.controlplane.ControlPlaneProperties;
import com.autoapi.controlplane.DatabaseReadinessChecker;
import com.autoapi.gateway.config.ActiveRuntimeBundle;
import com.autoapi.gateway.config.ActiveRuntimeConfigHolder;
import com.autoapi.runtime.AutoApiRole;
import com.autoapi.runtime.AutoApiRuntimeProperties;
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

  private AutoApiRuntimeProperties runtimeProperties;
  private ActiveRuntimeConfigHolder activeRuntimeConfigHolder;

  @BeforeEach
  void setUp() {
    runtimeProperties = new AutoApiRuntimeProperties();
    runtimeProperties.setRole(AutoApiRole.GATEWAY);
    activeRuntimeConfigHolder = new ActiveRuntimeConfigHolder();
    activeRuntimeConfigHolder.activate(
        new ActiveRuntimeBundle(
            null,
            0,
            "static",
            new RuntimeConfig(
                new GatewayConfig("0.0.0.0", 8080),
                List.of(
                    new RouteConfig(
                        "orders",
                        "api.autoapi.local",
                        "/v1/orders",
                        Set.of(HttpMethod.GET),
                        new UpstreamConfig(URI.create("http://127.0.0.1:9080")))))));
  }

  @Test
  void isReadyReturnsFalseBeforeApplicationReadyEvent() {
    GatewayReadiness readiness = buildReadiness(Optional.empty(), Optional.empty());
    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void isReadyReturnsTrueWhenGatewayHasActiveConfigAndNoDatabaseRequired() {
    GatewayReadiness readiness = buildReadiness(Optional.empty(), Optional.empty());
    readiness.onReady();
    StepVerifier.create(readiness.isReady()).expectNext(true).verifyComplete();
  }

  @Test
  void isReadyReturnsTrueWhenDatabaseCheckSucceeds() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady()).thenReturn(Mono.just(true));
    runtimeProperties.setRole(AutoApiRole.COMBINED);
    ControlPlaneProperties controlPlaneProperties = new ControlPlaneProperties();
    controlPlaneProperties.setEnabled(true);
    ControlPlaneProperties.CompiledGatewayProperties compiled =
        new ControlPlaneProperties.CompiledGatewayProperties();
    compiled.setListenAddress("0.0.0.0");
    compiled.setPort(8080);
    controlPlaneProperties.setCompiledGateway(compiled);
    GatewayReadiness readiness =
        buildReadiness(Optional.of(controlPlaneProperties), Optional.of(databaseReadinessChecker));
    readiness.onReady();
    StepVerifier.create(readiness.isReady()).expectNext(true).verifyComplete();
  }

  @Test
  void isReadyReturnsFalseWhenDatabaseCheckReturnsFalse() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady()).thenReturn(Mono.just(false));
    runtimeProperties.setRole(AutoApiRole.COMBINED);
    ControlPlaneProperties controlPlaneProperties = new ControlPlaneProperties();
    controlPlaneProperties.setEnabled(true);
    ControlPlaneProperties.CompiledGatewayProperties compiled =
        new ControlPlaneProperties.CompiledGatewayProperties();
    compiled.setListenAddress("0.0.0.0");
    compiled.setPort(8080);
    controlPlaneProperties.setCompiledGateway(compiled);
    GatewayReadiness readiness =
        buildReadiness(Optional.of(controlPlaneProperties), Optional.of(databaseReadinessChecker));
    readiness.onReady();
    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void isReadyReturnsFalseWhenDatabasePublisherFails() {
    DatabaseReadinessChecker databaseReadinessChecker = mock(DatabaseReadinessChecker.class);
    when(databaseReadinessChecker.isDatabaseReady())
        .thenReturn(Mono.error(new IllegalStateException("connection refused")));
    runtimeProperties.setRole(AutoApiRole.COMBINED);
    ControlPlaneProperties controlPlaneProperties = new ControlPlaneProperties();
    controlPlaneProperties.setEnabled(true);
    ControlPlaneProperties.CompiledGatewayProperties compiled =
        new ControlPlaneProperties.CompiledGatewayProperties();
    compiled.setListenAddress("0.0.0.0");
    compiled.setPort(8080);
    controlPlaneProperties.setCompiledGateway(compiled);
    GatewayReadiness readiness =
        buildReadiness(Optional.of(controlPlaneProperties), Optional.of(databaseReadinessChecker));
    readiness.onReady();
    StepVerifier.create(readiness.isReady()).expectNext(false).verifyComplete();
  }

  @Test
  void isReadyReturnsFalseWhenNoActiveGatewayConfig() {
    GatewayReadiness readiness =
        new GatewayReadiness(
            runtimeProperties,
            Optional.empty(),
            Optional.empty(),
            new ActiveRuntimeConfigHolder(),
            Optional.empty());
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

  private GatewayReadiness buildReadiness(
      Optional<ControlPlaneProperties> controlPlaneProperties,
      Optional<DatabaseReadinessChecker> databaseReadinessChecker) {
    RuntimeConfigHolder runtimeConfigHolder =
        new RuntimeConfigHolder(activeRuntimeConfigHolder.getActive().runtimeConfig());
    return new GatewayReadiness(
        runtimeProperties,
        controlPlaneProperties,
        Optional.of(runtimeConfigHolder),
        activeRuntimeConfigHolder,
        databaseReadinessChecker);
  }
}
