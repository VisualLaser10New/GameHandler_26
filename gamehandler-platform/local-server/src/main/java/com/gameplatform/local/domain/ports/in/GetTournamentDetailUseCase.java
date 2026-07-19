package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.TournamentDetailDto;

import java.util.Optional;

/**
 * Use case per la lettura del dettaglio di un torneo. Aggrega le
 * informazioni provenienti dalle quattro tabelle locali di replica
 * (riepilogo, classifica, partite e partecipanti) in un unico DTO.
 *
 * @see com.gameplatform.shared.dto.TournamentDetailDto
 */
public interface GetTournamentDetailUseCase {

    /**
     * Restituisce la vista dettagliata del torneo specificato.
     *
     * @param tournamentId identificativo del torneo
     * @return un {@code Optional} contenente il DTO di dettaglio, oppure vuoto se non trovato
     */
    Optional<TournamentDetailDto> getDetail(String tournamentId);
}