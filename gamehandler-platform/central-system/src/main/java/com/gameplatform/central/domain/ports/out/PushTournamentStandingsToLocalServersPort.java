package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code TOURNAMENT_STANDINGS_UPSERTED}
 * events to a single Local Server's
 * {@code PUT /internal/tournaments/standings/sync} endpoint. Structural twin
 * of {@link PushTournamentSummaryToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is a delete+insert snapshot by
 * {@code tournamentId}, so a transient transport failure just retries via the
 * outbox on the next scheduler tick.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushTournamentStandingsToLocalServersPort {

    /**
     * Pushes a batch of tournament-standings upsert events to a single local
     * server.
     *
     * @param events the standings DTO batch to push
     * @param server the single target active local server
     */
    void push(List<TournamentStandingsEventDto> events, RegisteredLocalServer server);
}