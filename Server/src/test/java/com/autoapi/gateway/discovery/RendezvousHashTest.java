package com.autoapi.gateway.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapi.config.RuntimeDiscoveredInstance;
import com.autoapi.config.RuntimeDiscoveredServiceConfig;
import com.autoapi.controlplane.discovery.DiscoveredServiceCompiler;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RendezvousHashTest {

  @Test
  void selectionIsDeterministic() {
    UUID serviceId = UUID.randomUUID();
    RuntimeDiscoveredServiceConfig service =
        new RuntimeDiscoveredServiceConfig(
            serviceId, "CONSISTENT_HASH", "REQUEST_ID", null, 1L, instances(serviceId));
    RuntimeDiscoveredInstance first =
        RendezvousHash.select(service, "sticky-key", service.instances());
    RuntimeDiscoveredInstance second =
        RendezvousHash.select(service, "sticky-key", service.instances());
    assertThat(first.instanceId()).isEqualTo(second.instanceId());
  }

  private static List<RuntimeDiscoveredInstance> instances(UUID serviceId) {
    return List.of(
        instance(serviceId, "a", 1), instance(serviceId, "b", 2), instance(serviceId, "c", 3));
  }

  private static RuntimeDiscoveredInstance instance(UUID serviceId, String instanceId, long epoch) {
    UUID targetId = DiscoveredServiceCompiler.compiledTargetId(serviceId, instanceId, epoch);
    return new RuntimeDiscoveredInstance(
        targetId, instanceId, URI.create("http://127.0.0.1:8080"), 100, null, null, epoch);
  }
}
