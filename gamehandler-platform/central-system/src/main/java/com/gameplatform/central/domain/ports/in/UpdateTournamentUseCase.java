package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;
import java.time.Instant;
import java.util.List;

/**
 * Use case §7.A.1: updates the mutable fields ({@code name}, {@code startsAt},
 * {@code buildingIds}) of a tournament still in {@code DRAFT} status. The
 * domain guard rejects updates once the tournament has left {@code DRAFT}.
 *
 * <p>{@code originatingRequestId} is nullable: {@code null} for the direct REST
 * branch (PUT /api/tournaments/{id}) and non-null for the SyncEventProcessor
 * branch §7.A.3, where it carries the id of the originating outbox event for
 * idempotency tracking.</p>
 */
public interface UpdateTournamentUseCase {
    TournamentDto update(TournamentId tournamentId, String name, Instant startsAt,
                         List<String> buildingIds, String originatingRequestId);
}