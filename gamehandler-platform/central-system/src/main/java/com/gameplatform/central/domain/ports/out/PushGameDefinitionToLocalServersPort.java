package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import java.util.List;

/**
 * Pushes a batch of {@code GAME_DEFINITION_UPSERTED} metadata events to a single
 * active Local server. Idempotent on the receiver side by composite PK
 * (game_type) — no ack/poison contract required.
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushGameDefinitionToLocalServersPort {
    void pushGameDefinitions(List<GameDefinitionEventDto> events, RegisteredLocalServer server);
}
