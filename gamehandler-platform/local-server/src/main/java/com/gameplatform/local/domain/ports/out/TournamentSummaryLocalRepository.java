package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code tournaments_summary_local} read-only replica (PIANO
 * §7.B). Mirror of {@link TournamentMatchLocalRepository} and
 * {@link GameDefinitionLocalRepository}. {@code save} is an idempotent upsert
 * by PK {@code tournamentId}; the projection row is physically removed on a
 * tombstone ({@code deleted=true}) via {@link #deleteById(TournamentId)}.
 */
public interface TournamentSummaryLocalRepository {

    TournamentSummaryLocal save(TournamentSummaryLocal summary);

    Optional<TournamentSummaryLocal> findById(TournamentId tournamentId);

    List<TournamentSummaryLocal> findAll();

    void deleteById(TournamentId tournamentId);

    boolean existsById(TournamentId tournamentId);
}
