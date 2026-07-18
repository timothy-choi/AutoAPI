package com.autoapi.controlplane.discovery;

import com.autoapi.controlplane.discovery.ServiceRegistrationCredentialService.CreatedRegistrationCredential;
import java.util.UUID;

public record CreatedRegistrationCredentialResponse(
    UUID id,
    UUID serviceId,
    String credentialId,
    String name,
    boolean enabled,
    String plaintextToken) {

  public static CreatedRegistrationCredentialResponse from(CreatedRegistrationCredential created) {
    return new CreatedRegistrationCredentialResponse(
        created.id(),
        created.serviceId(),
        created.credentialId(),
        created.name(),
        created.enabled(),
        created.plaintextToken());
  }
}
