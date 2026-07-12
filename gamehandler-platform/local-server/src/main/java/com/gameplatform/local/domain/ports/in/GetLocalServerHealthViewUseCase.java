package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.ServerHealthViewDto;

/**
 * Use case (PIANO §7.B): aggregates this Local node's own pending-outbox
 * count with the registry of all known registered local servers, for
 * the {@code GET /api/admin/servers/health} PLATFORM_ADMIN endpoint.
 */
public interface GetLocalServerHealthViewUseCase {

    ServerHealthViewDto getHealthView();
}