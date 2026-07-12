package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;

/**
 * Outbox emission contract for a newly-scheduled tournament match.
 *
 * <p>The implementing infrastructure adapter ({@code TournamentMatchOutboxAdapter})
 * is responsible for building the {@code TournamentMatchScheduledDto} with a fresh
 * shared UUID, JSON-serialising it, and persisting the {@code OutboxEvent} row of
 * type {@code "TOURNAMENT_MATCH_SCHEDULED"} within the caller's active transaction
 * (Outbox Pattern, mirroring {@code LocalAdminBuildingService.writeOutboxEvent}).
 * The row remains {@code PENDING} until the FASE 6 scheduler drains it and pushes
 * it to the involved Local servers.</p>
 *
 * <p>This port deliberately depends only on domain types
 * ({@link TournamentMatch}, {@link Tournament}) and NOT on {@code shared-dto}
 * or {@code OutboxEvent}; this keeps the domain layer free of infrastructure
 * and DTO surface concerns. The adapter owns the DTO construction and the
 * UUID generation.</p>
 *
 * <p>Implementations MUST NEVER be called for {@code BYE} rows — a BYE is an
 * auto-advancement, not a match scheduled for play at a building, and must
 * not be replicated to Local in FASE 6. The sole caller
 * ({@code TournamentBracketService.schedule}) enforces this guard.</p>
 */
public interface TournamentMatchOutboxPort {
    /**
     * Atomically writes (within the caller's tx) a single outbox event of type
     * {@code "TOURNAMENT_MATCH_SCHEDULED"} for the given {@code SCHEDULED} match.
     *
     * @param match      the newly-scheduled match (MUST have status
     *                   {@link com.gameplatform.shared.domain.model.TournamentMatchStatus#SCHEDULED};
     *                   MUST NOT be a BYE row)
     * @param tournament the parent tournament (provides {@code gameType} for
     *                   the denormalised outbox payload since the match has no
     *                   game_type column)
     */
    void publishScheduled(TournamentMatch match, Tournament tournament);
}
