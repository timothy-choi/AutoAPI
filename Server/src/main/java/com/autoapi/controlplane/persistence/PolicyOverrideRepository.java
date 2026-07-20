package com.autoapi.controlplane.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PolicyOverrideRepository
    extends ReactiveCrudRepository<PolicyOverrideEntity, UUID> {}
