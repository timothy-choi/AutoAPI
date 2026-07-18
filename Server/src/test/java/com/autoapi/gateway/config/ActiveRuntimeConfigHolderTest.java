package com.autoapi.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autoapi.config.GatewayConfig;
import com.autoapi.config.RouteConfig;
import com.autoapi.config.RuntimeConfig;
import com.autoapi.config.UpstreamConfig;
import com.autoapi.config.UpstreamTargetReference;
import com.autoapi.controlplane.configversion.CompiledGatewaySection;
import com.autoapi.controlplane.configversion.HashableRuntimePayload;
import com.autoapi.controlplane.configversion.RuntimeConfigCompiler;
import com.autoapi.controlplane.configversion.RuntimeContentHasher;
import com.autoapi.controlplane.configversion.StoredRuntimeSnapshot;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.UpstreamPoolEntity;
import com.autoapi.controlplane.persistence.UpstreamTargetEntity;
import com.autoapi.gateway.GatewayProperties;
import com.autoapi.gateway.health.HealthAwareTargetSelector;
import com.autoapi.gateway.health.TargetHealthRegistry;
import com.autoapi.gateway.traffic.TrafficSplitRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;

class ActiveRuntimeConfigHolderTest {

  private static final UUID API_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final CompiledGatewaySection GATEWAY = new CompiledGatewaySection("0.0.0.0", 8080);

  private ActiveRuntimeConfigHolder holder;
  private LocalGatewayConfigActivator activator;
  private HealthAwareTargetSelector targetSelector;

  @BeforeEach
  void setUp() {
    holder = new ActiveRuntimeConfigHolder();
    GatewayProperties gatewayProperties = new GatewayProperties();
    gatewayProperties.setApiId(API_ID);
    targetSelector =
        new HealthAwareTargetSelector(
            new TargetHealthRegistry(Clock.systemUTC()), Clock.systemUTC());
    activator =
        new LocalGatewayConfigActivator(
            holder,
            gatewayProperties,
            registryProvider(new TargetHealthRegistry(Clock.systemUTC())),
            retryBudgetProvider(
                new com.autoapi.gateway.retry.RetryBudgetRegistry(Clock.systemUTC())),
            trafficSplitProvider(new TrafficSplitRegistry()));
  }

  private static ObjectProvider<TrafficSplitRegistry> trafficSplitProvider(
      TrafficSplitRegistry registry) {
    return new ObjectProvider<>() {
      @Override
      public TrafficSplitRegistry getObject() {
        return registry;
      }

      @Override
      public TrafficSplitRegistry getObject(Object... args) {
        return registry;
      }

      @Override
      public TrafficSplitRegistry getIfAvailable() {
        return registry;
      }

      @Override
      public TrafficSplitRegistry getIfAvailable(
          java.util.function.Supplier<TrafficSplitRegistry> defaultSupplier) {
        return registry;
      }

      @Override
      public TrafficSplitRegistry getIfUnique() {
        return registry;
      }

      @Override
      public void ifAvailable(Consumer<TrafficSplitRegistry> dependencyConsumer) {
        dependencyConsumer.accept(registry);
      }

      @Override
      public java.util.stream.Stream<TrafficSplitRegistry> stream() {
        return java.util.stream.Stream.of(registry);
      }

      @Override
      public java.util.Iterator<TrafficSplitRegistry> iterator() {
        return List.of(registry).iterator();
      }
    };
  }

  private static ObjectProvider<com.autoapi.gateway.retry.RetryBudgetRegistry> retryBudgetProvider(
      com.autoapi.gateway.retry.RetryBudgetRegistry registry) {
    return new ObjectProvider<>() {
      @Override
      public com.autoapi.gateway.retry.RetryBudgetRegistry getObject() {
        return registry;
      }

      @Override
      public com.autoapi.gateway.retry.RetryBudgetRegistry getObject(Object... args) {
        return registry;
      }

      @Override
      public com.autoapi.gateway.retry.RetryBudgetRegistry getIfAvailable() {
        return registry;
      }

      @Override
      public com.autoapi.gateway.retry.RetryBudgetRegistry getIfAvailable(
          java.util.function.Supplier<com.autoapi.gateway.retry.RetryBudgetRegistry>
              defaultSupplier) {
        return registry;
      }

      @Override
      public com.autoapi.gateway.retry.RetryBudgetRegistry getIfUnique() {
        return registry;
      }

      @Override
      public void ifAvailable(
          java.util.function.Consumer<com.autoapi.gateway.retry.RetryBudgetRegistry>
              dependencyConsumer) {
        dependencyConsumer.accept(registry);
      }

      @Override
      public java.util.stream.Stream<com.autoapi.gateway.retry.RetryBudgetRegistry> stream() {
        return java.util.stream.Stream.of(registry);
      }

      @Override
      public java.util.Iterator<com.autoapi.gateway.retry.RetryBudgetRegistry> iterator() {
        return java.util.List.of(registry).iterator();
      }
    };
  }

  private static ObjectProvider<TargetHealthRegistry> registryProvider(
      TargetHealthRegistry registry) {
    return new ObjectProvider<>() {
      @Override
      public TargetHealthRegistry getObject() {
        return registry;
      }

      @Override
      public TargetHealthRegistry getObject(Object... args) {
        return registry;
      }

      @Override
      public TargetHealthRegistry getIfAvailable() {
        return registry;
      }

      @Override
      public TargetHealthRegistry getIfAvailable(
          java.util.function.Supplier<TargetHealthRegistry> defaultSupplier) {
        return registry;
      }

      @Override
      public void ifAvailable(Consumer<TargetHealthRegistry> dependencyConsumer) {
        dependencyConsumer.accept(registry);
      }

      @Override
      public TargetHealthRegistry getIfUnique() {
        return registry;
      }

      @Override
      public java.util.stream.Stream<TargetHealthRegistry> stream() {
        return java.util.stream.Stream.of(registry);
      }

      @Override
      public java.util.Iterator<TargetHealthRegistry> iterator() {
        return List.of(registry).iterator();
      }
    };
  }

  @Test
  void atomicActivationReplacesReference() {
    ActiveRuntimeBundle first = bundleWithVersion(1);
    ActiveRuntimeBundle second = bundleWithVersion(2);

    holder.activate(first);
    assertSame(first, holder.getActive());

    holder.activate(second);
    assertSame(second, holder.getActive());
    assertEquals(2, holder.getActive().version());
  }

  @Test
  void failedCandidateDoesNotReplaceActiveConfig() {
    ActiveRuntimeBundle active = bundleWithVersion(1);
    holder.activate(active);

    StoredRuntimeSnapshot invalid = snapshotWithTamperedHash(1);
    assertFalse(activator.activateCandidate(invalid).success());

    assertSame(active, holder.getActive());
    assertEquals(1, holder.getActive().version());
  }

  @Test
  void roundRobinSelectionRotates() {
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000040");
    UUID targetAId = UUID.fromString("00000000-0000-0000-0000-000000000041");
    UUID targetBId = UUID.fromString("00000000-0000-0000-0000-000000000042");
    RouteConfig route =
        new RouteConfig(
            "orders",
            "api.autoapi.local",
            "/v1/orders",
            Set.of(HttpMethod.GET),
            UpstreamConfig.roundRobin(
                poolId,
                List.of(
                    new UpstreamTargetReference(targetAId, URI.create("http://upstream-a:8080"), 1),
                    new UpstreamTargetReference(
                        targetBId, URI.create("http://upstream-b:8080"), 1)),
                null));
    ActiveRuntimeBundle bundle =
        new ActiveRuntimeBundle(
            API_ID,
            1,
            "round-robin",
            new RuntimeConfig(new GatewayConfig("0.0.0.0", 8080), List.of(route)));
    holder.activate(bundle);

    var selectedA =
        targetSelector.select(API_ID, route.upstream().poolId(), route.upstream().targets(), null);
    var selectedB =
        targetSelector.select(API_ID, route.upstream().poolId(), route.upstream().targets(), null);
    var selectedC =
        targetSelector.select(API_ID, route.upstream().poolId(), route.upstream().targets(), null);
    assertEquals(URI.create("http://upstream-a:8080"), selectedA.target().url());
    assertEquals(URI.create("http://upstream-b:8080"), selectedB.target().url());
    assertEquals(URI.create("http://upstream-a:8080"), selectedC.target().url());
    assertTrue(holder.hasActiveConfig());
  }

  private static ActiveRuntimeBundle bundleWithVersion(long version) {
    StoredRuntimeSnapshot snapshot = validSnapshot(version);
    return new ActiveRuntimeBundle(
        snapshot.apiId(),
        snapshot.version(),
        snapshot.contentHash(),
        new RuntimeConfig(
            new GatewayConfig(snapshot.gateway().listenAddress(), snapshot.gateway().port()),
            List.of(
                new RouteConfig(
                    snapshot.routes().getFirst().id().toString(),
                    snapshot.routes().getFirst().host(),
                    snapshot.routes().getFirst().pathPrefix(),
                    Set.of(HttpMethod.GET),
                    UpstreamConfig.single(URI.create("http://upstream-v1:8080"))))));
  }

  private static StoredRuntimeSnapshot validSnapshot(long version) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID poolId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000030");

    UpstreamPoolEntity pool =
        new UpstreamPoolEntity(poolId, API_ID, "orders-v1", "ROUND_ROBIN", null, now, now);
    UpstreamTargetEntity target =
        new UpstreamTargetEntity(targetId, poolId, "http://upstream-v1:8080", true, 1, now, now);
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
        RuntimeConfigCompiler.compileWithoutSecurity(
            API_ID, GATEWAY, List.of(route), Map.of(poolId, pool), Map.of(poolId, List.of(target)));
    String hash = RuntimeContentHasher.sha256Hex(RuntimeContentHasher.canonicalJson(payload));
    return RuntimeConfigCompiler.toStoredSnapshot(payload, version, hash);
  }

  private static StoredRuntimeSnapshot snapshotWithTamperedHash(long version) {
    StoredRuntimeSnapshot snapshot = validSnapshot(version);
    return new StoredRuntimeSnapshot(
        snapshot.apiId(),
        snapshot.version(),
        "deadbeef",
        snapshot.gateway(),
        snapshot.routes(),
        snapshot.apiKeys());
  }
}
