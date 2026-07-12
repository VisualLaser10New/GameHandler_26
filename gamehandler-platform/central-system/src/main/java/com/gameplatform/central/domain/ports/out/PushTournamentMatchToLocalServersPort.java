package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code TOURNAMENT_MATCH_SCHEDULED}
 * events to a single Local Server's {@code PUT /internal/tournaments/matches/sync}
 * endpoint. Structural twin of {@link PushGameDefinitionToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is idempotent by PK
 * ({@code matchId}), so a transient transport failure just retries via the
 * outbox on the next scheduler tick.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushTournamentMatchToLocalServersPort {

    /**
     * Pushes a batch of tournament-match scheduled events to a single local
     * server.
     *
     * @param events the scheduled-match DTO batch to push (one per involved
     *               match routed to this building)
     * @param server the single target active local server
     */
    void pushTournamentMatch(List<TournamentMatchScheduledDto> events, RegisteredLocalServer server);
}