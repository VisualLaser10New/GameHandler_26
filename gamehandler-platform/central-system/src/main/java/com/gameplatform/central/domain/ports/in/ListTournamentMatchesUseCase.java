package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentMatchDto;

import java.util.List;

/**
 * Read-only delegation to {@code TournamentMatchRepository.findByTournament}.
 * Returns the full match list of a tournament — including {@code BYE} rows.
 * No status filtering applied.
 */
public interface ListTournamentMatchesUseCase {

    /**
     * Restituisce l'elenco completo degli incontri di un torneo, incluse le righe {@code BYE}.
     *
     * @param tournamentId l'identificativo del torneo di cui recuperare gli incontri; non deve essere {@code null}
     * @return la lista di {@link TournamentMatchDto} rappresentante gli incontri; la lista è vuota se il torneo non ha incontri
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     */
    List<TournamentMatchDto> findByTournament(TournamentId tournamentId);
}
