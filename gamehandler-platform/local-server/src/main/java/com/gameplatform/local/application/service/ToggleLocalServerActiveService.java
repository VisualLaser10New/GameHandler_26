package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.in.ToggleLocalServerActiveUseCase;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Feature 3 — write use case backing the
 * {@code PATCH /api/admin/servers/{buildingId}/active} PLATFORM_ADMIN endpoint.
 * Updates the locally replicated {@code registered_local_servers_local} row's
 * {@code active} flag (see PIANO §7.B). The projection is a Central→Local
 * replica, so a manual toggle here is observed locally until the next registry
 * sync cycle resynchronises it from the central {@code local_servers} table.
 */
@Service
@Transactional
public class ToggleLocalServerActiveService implements ToggleLocalServerActiveUseCase {

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;

    public ToggleLocalServerActiveService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
    }

    @Override
    public Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return registeredLocalServerLocalRepository.setActive(buildingId, active);
    }
}