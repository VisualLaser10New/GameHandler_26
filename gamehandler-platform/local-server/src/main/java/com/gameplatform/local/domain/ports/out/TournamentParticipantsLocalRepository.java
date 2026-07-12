package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;

/**
 * Out-port for the {@code tournament_participants_local} read-only
 * replica (PIANO §7.B). {@code save} is an idempotent upsert by the
 * composite PK ({@code tournamentId}, {@code participantId}); the sync
 * service physically removes a tournament's full participant snapshot
 * via {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency). {@link #deleteByTournamentAndParticipantId} targets an
 * individual registration row.
 */
public interface TournamentParticipantsLocalRepository {

    TournamentParticipantLocal save(TournamentParticipantLocal participant);

    List<TournamentParticipantLocal> findByTournament(TournamentId tournamentId);

    void deleteByTournament(TournamentId tournamentId);

    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}