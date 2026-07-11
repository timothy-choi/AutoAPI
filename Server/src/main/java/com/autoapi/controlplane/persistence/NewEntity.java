package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Marks application-assigned IDs as new rows so Spring Data R2DBC performs INSERT instead of
 * UPDATE-on-missing-row during create flows.
 */
public interface NewEntity extends Persistable<UUID> {

  UUID id();

  @Override
  default UUID getId() {
    return id();
  }

  @Override
  default boolean isNew() {
    return true;
  }
}
