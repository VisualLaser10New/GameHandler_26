package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code TOURNAMENT_SUMMARY_UPSERTED}
 * events to a single Local Server's
 * {@code PUT /internal/tournaments/summaries/sync} endpoint. Structural twin
 * of {@link PushTournamentMatchToLocalServersPort} and
 * {@link PushGameDefinitionToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is idempotent by PK
 * ({@code tournamentId}), so a transient transport failure just retries via
 * the outbox on the next scheduler tick. A {@code deleted=true} tombstone is
 * handled by the local side as a {@code deleteById} (projection removal).</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         on transient transport failure (caller retries via the outbox)
 */
public interface PushTournamentSummaryToLocalServersPort {

    /**
     * Pushes a batch of tournament-summary upsert events to a single local
     * server.
     *
     * @param events the summary DTO batch to push
     * @param server the single target active local server
     */
    void push(List<TournamentSummaryEventDto> events, RegisteredLocalServer server);
}