package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of LOCAL_ADMIN&harr;building metadata events
 * to a single Local Server's {@code PUT /internal/metadata/sync} endpoint.
 *
 * <p>Symmetric to {@link PushUserToLocalServersPort} but for metadata. There is
 * no ack contract / poison-isolation here: the local upsert/delete is idempotent
 * by composite PK, so a transient transport failure just retried via the outbox
 * on the next scheduler tick — it can never produce a "poison" event.</p>
 */
public interface PushMetadataToLocalServersPort {
    /**
     * Pushes a batch of metadata events to a single local server.
     *
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         on transient transport failure (caller retries via the outbox)
     */
    void pushMetadata(List<LocalAdminBuildingEventDto> events, RegisteredLocalServer server);
}