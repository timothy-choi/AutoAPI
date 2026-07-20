package com.autoapi.controlplane.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EffectivePolicyCacheMetadataRepository
    extends ReactiveCrudRepository<EffectivePolicyCacheMetadataEntity, String> {}
