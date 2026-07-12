package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code LOCAL_SERVER_REGISTRY_UPSERTED}
 * events to a single Local Server's {@code PUT /internal/servers/sync}
 * endpoint. Structural twin of {@link PushTournamentSummaryToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is idempotent by PK
 * ({@code buildingId}), so a transient transport failure just retries via the
 * outbox on the next scheduler tick. Exposing {@code registered_local_servers}
 * to every Local lets a PLATFORM_ADMIN client (connected to any Local) see the
 * full registry without a direct Central call (E1).</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushLocalServerRegistryToLocalServersPort {

    /**
     * Pushes a batch of local-server-registry upsert events to a single local
     * server.
     *
     * @param events the registry DTO batch to push
     * @param server the single target active local server
     */
    void push(List<LocalServerRegistryEventDto> events, RegisteredLocalServer server);
}