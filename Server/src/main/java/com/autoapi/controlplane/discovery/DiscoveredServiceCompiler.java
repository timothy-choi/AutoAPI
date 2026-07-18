package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.configversion.CompiledDiscoveredInstanceSection;
import com.autoapi.controlplane.configversion.CompiledDiscoveredServiceSection;
import com.autoapi.controlplane.persistence.DiscoveredServiceEntity;
import com.autoapi.controlplane.persistence.ServiceInstanceEntity;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DiscoveredServiceCompiler {

  private DiscoveredServiceCompiler() {}

  public static CompiledDiscoveredServiceSection compileService(
      DiscoveredServiceEntity service,
      List<ServiceInstanceEntity> instances,
      OffsetDateTime compileInstant) {
    List<CompiledDiscoveredInstanceSection> compiledInstances =
        instances.stream()
            .filter(instance -> isEligible(instance, service, compileInstant))
            .sorted(Comparator.comparing(ServiceInstanceEntity::instanceId))
            .map(DiscoveredServiceCompiler::compileInstance)
            .toList();
    return new CompiledDiscoveredServiceSection(
        service.id(),
        service.selectionStrategy(),
        service.consistentHashKey(),
        service.consistentHashKeyName(),
        service.membershipVersion(),
        compiledInstances);
  }

  public static boolean isEligible(
      ServiceInstanceEntity instance, DiscoveredServiceEntity service, OffsetDateTime now) {
    if (!service.enabled()) {
      return false;
    }
    if (!"READY".equals(instance.status())) {
      return false;
    }
    return !instance.leaseExpiresAt().isBefore(now);
  }

  public static CompiledDiscoveredInstanceSection compileInstance(ServiceInstanceEntity instance) {
    String url = instance.scheme() + "://" + instance.host() + ":" + instance.port();
    return new CompiledDiscoveredInstanceSection(
        compiledTargetId(instance.serviceId(), instance.instanceId(), instance.registrationEpoch()),
        instance.instanceId(),
        url,
        instance.weight(),
        instance.zone(),
        instance.region(),
        instance.registrationEpoch());
  }

  public static UUID compiledTargetId(UUID serviceId, String instanceId, long registrationEpoch) {
    String material = serviceId + ":" + instanceId + ":" + registrationEpoch;
    return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
  }
}
