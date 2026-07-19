package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;

/**
 * Caso d'uso per l'annullamento di un torneo esistente.
 *
 * <p>Fornisce l'operazione per portare un torneo nello stato annullato,
 * verificando che lo stato corrente del torneo lo consenta. Supporta
 * sia chiamate con tracciabilità tramite {@code originatingRequestId}
 * sia chiamate REST dirette senza identificativo di origine.</p>
 *
 * @see com.gameplatform.central.application.service.TournamentService
 * @see com.gameplatform.shared.domain.exception.InvalidTournamentStateException
 */
public interface CancelTournamentUseCase {

    /**
     * Annulla il torneo indicato e ne restituisce lo stato aggiornato.
     *
     * @param tournamentId l'identificativo del torneo da annullare; non deve essere {@code null}
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return il {@link TournamentDto} rappresentante il torneo nello stato annullato
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se lo stato del torneo non consente l'annullamento
     * @see #cancel(TournamentId)
     */
    TournamentDto cancel(TournamentId tournamentId, String originatingRequestId);

    /**
     * Annulla il torneo indicato tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param tournamentId l'identificativo del torneo da annullare; non deve essere {@code null}
     * @return il {@link TournamentDto} rappresentante il torneo nello stato annullato
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se lo stato del torneo non consente l'annullamento
     * @see #cancel(TournamentId, String)
     */
    default TournamentDto cancel(TournamentId tournamentId) {
        return cancel(tournamentId, null);
    }
}