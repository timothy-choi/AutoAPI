package com.autoapi.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.gateway.config.remote.ControlPlaneConfigClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "autoapi.role=gateway",
      "autoapi.gateway.config-source=control-plane",
      "autoapi.gateway.id=test-gateway",
      "autoapi.controlplane.enabled=false",
      "spring.flyway.enabled=false"
    })
@ContextConfiguration(initializers = ControlPlaneConfigPollerTest.Initializer.class)
class ControlPlaneConfigPollerTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
  private static final StoredRuntimeSnapshot SNAPSHOT = buildSnapshot(1);
  private static final AtomicBoolean FAIL_DESIRED = new AtomicBoolean(false);
  private static final AtomicInteger DESIRED_REQUESTS = new AtomicInteger(0);
  private static final AtomicInteger SNAPSHOT_REQUESTS = new AtomicInteger(0);
  private static final AtomicInteger CONFIG_STATUS_REQUESTS = new AtomicInteger(0);
  private static final AtomicInteger REGISTER_REQUESTS = new AtomicInteger(0);
  private static final AtomicReference<String> LAST_IF_NONE_MATCH = new AtomicReference<>();

  private static HttpServer httpServer;
  private static int mockPort;

  static {
    try {
      httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      mockPort = httpServer.getAddress().getPort();
      httpServer.createContext(
          "/api/v1/gateway-config/" + API_ID + "/desired",
          ControlPlaneConfigPollerTest::handleDesired);
      httpServer.createContext(
          "/api/v1/gateway-config/" + API_ID + "/versions/1",
          ControlPlaneConfigPollerTest::handleSnapshot);
      httpServer.createContext(
          "/api/v1/gateways/register", ControlPlaneConfigPollerTest::handleRegister);
      httpServer.createContext(
          "/api/v1/gateways/test-gateway/config-status",
          ControlPlaneConfigPollerTest::handleConfigStatus);
      httpServer.createContext(
          "/api/v1/gateways/test-gateway/heartbeat", ControlPlaneConfigPollerTest::handleHeartbeat);
      httpServer.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private ActiveRuntimeConfigHolder activeRuntimeConfigHolder;
  @Autowired private ControlPlaneConfigPoller controlPlaneConfigPoller;

  @AfterAll
  static void stopMockControlPlane() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  @DynamicPropertySource
  static void gatewayProperties(DynamicPropertyRegistry registry) {
    registry.add("autoapi.gateway.api-id", () -> API_ID);
    registry.add("autoapi.gateway.id", () -> "test-gateway");
    registry.add("autoapi.gateway.control-plane-base-url", () -> "http://127.0.0.1:" + mockPort);
    registry.add("autoapi.gateway.poll-interval", () -> "100ms");
    registry.add("autoapi.gateway.heartbeat-interval", () -> "1h");
  }

  @BeforeEach
  void resetMockState() {
    FAIL_DESIRED.set(false);
    LAST_IF_NONE_MATCH.set(null);
  }

  @Test
  void initialFetchActivatesConfig() {
    assertNotNull(controlPlaneConfigPoller);
    awaitUntil(() -> activeRuntimeConfigHolder.hasActiveConfig(), Duration.ofSeconds(10));

    ActiveRuntimeBundle active = activeRuntimeConfigHolder.getActive();
    assertNotNull(active);
    assertEquals(API_ID, active.apiId());
    assertEquals(1, active.version());
    assertEquals(SNAPSHOT.contentHash(), active.contentHash());
    assertEquals("/v1/orders", active.runtimeConfig().routes().getFirst().pathPrefix());
    assertTrue(DESIRED_REQUESTS.get() >= 1);
    assertTrue(SNAPSHOT_REQUESTS.get() >= 1);
    assertTrue(CONFIG_STATUS_REQUESTS.get() >= 1);
  }

  @Test
  void notModifiedDoesNotReplaceActiveConfig() {
    awaitUntil(() -> activeRuntimeConfigHolder.hasActiveConfig(), Duration.ofSeconds(10));
    ActiveRuntimeBundle activeBefore = activeRuntimeConfigHolder.getActive();
    int desiredBefore = DESIRED_REQUESTS.get();
    int snapshotBefore = SNAPSHOT_REQUESTS.get();

    awaitUntil(() -> DESIRED_REQUESTS.get() > desiredBefore, Duration.ofSeconds(10));

    assertSame(activeBefore, activeRuntimeConfigHolder.getActive());
    assertEquals(snapshotBefore, SNAPSHOT_REQUESTS.get());
    assertNotNull(LAST_IF_NONE_MATCH.get());
    assertTrue(LAST_IF_NONE_MATCH.get().contains(SNAPSHOT.contentHash()));
  }

  @Test
  void pollFailureRetainsPriorConfig() {
    awaitUntil(() -> activeRuntimeConfigHolder.hasActiveConfig(), Duration.ofSeconds(10));
    ActiveRuntimeBundle activeBefore = activeRuntimeConfigHolder.getActive();
    FAIL_DESIRED.set(true);
    int desiredBefore = DESIRED_REQUESTS.get();

    awaitUntil(() -> DESIRED_REQUESTS.get() >= desiredBefore + 2, Duration.ofSeconds(10));

    assertSame(activeBefore, activeRuntimeConfigHolder.getActive());
    assertEquals(1, activeRuntimeConfigHolder.getActive().version());
    assertEquals(SNAPSHOT.contentHash(), activeRuntimeConfigHolder.getActive().contentHash());
  }

  private static void handleRegister(HttpExchange exchange) throws IOException {
    REGISTER_REQUESTS.incrementAndGet();
    writeResponse(exchange, 201, "application/json", "{\"gatewayId\":\"test-gateway\"}");
  }

  private static void handleConfigStatus(HttpExchange exchange) throws IOException {
    CONFIG_STATUS_REQUESTS.incrementAndGet();
    writeResponse(exchange, 202, "application/json", "{\"accepted\":true,\"idempotent\":false}");
  }

  private static void handleHeartbeat(HttpExchange exchange) throws IOException {
    writeResponse(exchange, 200, "application/json", "{\"gatewayId\":\"test-gateway\"}");
  }

  private static void handleDesired(HttpExchange exchange) throws IOException {
    DESIRED_REQUESTS.incrementAndGet();
    LAST_IF_NONE_MATCH.set(exchange.getRequestHeaders().getFirst("If-None-Match"));

    if (FAIL_DESIRED.get()) {
      writeResponse(exchange, 500, "application/json", "{\"error\":\"mock failure\"}");
      return;
    }

    String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
    if (ifNoneMatch != null && ifNoneMatch.contains(SNAPSHOT.contentHash())) {
      exchange.getResponseHeaders().add(HttpHeaders.ETAG, "\"" + SNAPSHOT.contentHash() + "\"");
      exchange.sendResponseHeaders(304, -1);
      exchange.close();
      return;
    }

    String body =
        OBJECT_MAPPER.writeValueAsString(
            new ControlPlaneConfigClient.DesiredMetadataResponse(
                API_ID,
                SNAPSHOT.version(),
                SNAPSHOT.contentHash(),
                "/api/v1/gateway-config/" + API_ID + "/versions/" + SNAPSHOT.version()));
    writeResponse(exchange, 200, "application/json", body);
  }

  private static void handleSnapshot(HttpExchange exchange) throws IOException {
    SNAPSHOT_REQUESTS.incrementAndGet();
    String body = OBJECT_MAPPER.writeValueAsString(SNAPSHOT);
    exchange.getResponseHeaders().add(HttpHeaders.ETAG, "\"" + SNAPSHOT.contentHash() + "\"");
    writeResponse(exchange, 200, "application/json", body);
  }

  private static void writeResponse(
      HttpExchange exchange, int status, String contentType, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(bytes);
    }
    exchange.close();
  }

  private static StoredRuntimeSnapshot buildSnapshot(long version) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://127.0.0.1:19080", true, 1, now, now);
    RouteEntity route =
        new RouteEntity(
            routeId,
            API_ID,
            "orders-route",
            "api.autoapi.local",
            "/v1/orders",
            new String[] {"GET"},
            poolId,
            true,
            now,
            now);

    HashableRuntimePayload payload =
        RuntimeConfigCompiler.compile(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }

  private static void awaitUntil(
      java.util.concurrent.Callable<Boolean> condition, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      try {
        if (condition.call()) {
          return;
        }
      } catch (Exception e) {
        fail(e);
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail(e);
      }
    }
    fail("Condition not met within " + timeout);
  }

  static class Initializer
      implements org.springframework.context.ApplicationContextInitializer<
          org.springframework.context.ConfigurableApplicationContext> {

    @Override
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
      org.springframework.core.env.MapPropertySource propertySource =
          new org.springframework.core.env.MapPropertySource(
              "controlPlaneConfigPollerTest",
              Map.of(
                  "spring.autoconfigure.exclude",
                  String.join(
                      ",",
                      "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
                      "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration",
                      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                      "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")));
      context.getEnvironment().getPropertySources().addFirst(propertySource);
    }
  }
}
