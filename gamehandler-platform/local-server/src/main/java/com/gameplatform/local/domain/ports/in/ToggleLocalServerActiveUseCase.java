package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import java.util.Optional;

/**
 * Use case (Feature 3): toggles the {@code active} flag of the locally
 * replicated {@code registered_local_servers_local} row identified by the
 * given {@code buildingId}. Invoked by the
 * {@code PATCH /api/admin/servers/{buildingId}/active} PLATFORM_ADMIN endpoint.
 */
public interface ToggleLocalServerActiveUseCase {

    /**
     * Applies the requested {@code active} flag to the projection row.
     *
     * @return the updated projection, or {@link Optional#empty()} when no row
     *         exists locally for the given {@code buildingId} (404)
     */
    Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active);
}