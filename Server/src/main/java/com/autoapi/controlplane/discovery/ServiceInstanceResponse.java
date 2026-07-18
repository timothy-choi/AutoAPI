package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.persistence.ServiceInstanceEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceInstanceResponse(
    UUID id,
    UUID serviceId,
    String instanceId,
    String host,
    int port,
    String scheme,
    String zone,
    String region,
    int weight,
    String status,
    long registrationEpoch,
    OffsetDateTime registeredAt,
    OffsetDateTime lastHeartbeatAt,
    OffsetDateTime leaseExpiresAt,
    OffsetDateTime deregisteredAt,
    String metadata) {

  public static ServiceInstanceResponse from(ServiceInstanceEntity entity) {
    return new ServiceInstanceResponse(
        entity.id(),
        entity.serviceId(),
        entity.instanceId(),
        entity.host(),
        entity.port(),
        entity.scheme(),
        entity.zone(),
        entity.region(),
        entity.weight(),
        entity.status(),
        entity.registrationEpoch(),
        entity.registeredAt(),
        entity.lastHeartbeatAt(),
        entity.leaseExpiresAt(),
        entity.deregisteredAt(),
        entity.metadata());
  }
}
