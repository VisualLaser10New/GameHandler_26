package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code registered_local_servers_local} read-only
 * replica (PIANO §7.B). {@code save} is an idempotent upsert by PK
 * {@code buildingId}.
 */
public interface RegisteredLocalServerLocalRepository {

    RegisteredLocalServerLocal save(RegisteredLocalServerLocal server);

    Optional<RegisteredLocalServerLocal> findById(String buildingId);

    List<RegisteredLocalServerLocal> findAll();

    void deleteById(String buildingId);

    /**
     * Feature 3 — flips the {@code active} flag of the projection row keyed by
     * {@code buildingId}. Returns the updated row, or {@link Optional#empty()}
     * when no such building exists locally.
     */
    Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active);
}