package com.autoapi.controlplane.events;

import java.util.UUID;

public record EventContext(
    String correlationId, UUID causationId, String actorType, String actorId, String source) {

  public static EventContext managementApi(String correlationId) {
    return new EventContext(
        correlationId == null || correlationId.isBlank()
            ? UUID.randomUUID().toString()
            : correlationId,
        null,
        "API_CLIENT",
        "management-api",
        "MANAGEMENT_API");
  }

  public static EventContext fromPrincipal(
      String correlationId, com.autoapi.controlplane.managementauth.ManagementPrincipal principal) {
    if (principal == null) {
      return managementApi(correlationId);
    }
    return new EventContext(
        correlationId == null || correlationId.isBlank()
            ? UUID.randomUUID().toString()
            : correlationId,
        null,
        principal.principalType().actorType(),
        principal.principalId().toString(),
        principal.authenticationMethod().name());
  }

  public static EventContext system(String actorId, String source) {
    return new EventContext(UUID.randomUUID().toString(), null, "SYSTEM", actorId, source);
  }

  public static EventContext scheduledJob(String jobName, String source) {
    return new EventContext(UUID.randomUUID().toString(), null, "SCHEDULED_JOB", jobName, source);
  }

  public EventContext withCausation(UUID causationId) {
    return new EventContext(correlationId, causationId, actorType, actorId, source);
  }
}
