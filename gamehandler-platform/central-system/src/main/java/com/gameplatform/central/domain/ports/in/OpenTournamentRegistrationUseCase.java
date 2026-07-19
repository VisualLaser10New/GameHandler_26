package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;

/**
 * Caso d'uso per l'apertura delle iscrizioni a un torneo.
 *
 * <p>Permette di rendere un torneo disponibile per la registrazione
 * dei partecipanti, verificando che lo stato del torneo lo consenta.
 * Supporta sia chiamate con tracciabilità tramite {@code originatingRequestId}
 * sia chiamate REST dirette senza identificativo di origine.</p>
 *
 * @see com.gameplatform.central.application.service.TournamentRegistrationService
 * @see com.gameplatform.shared.domain.exception.InvalidTournamentStateException
 */
public interface OpenTournamentRegistrationUseCase {

    /**
     * Apre le iscrizioni del torneo indicato, rendendolo disponibile alla registrazione dei partecipanti.
     *
     * @param tournamentId l'identificativo del torneo di cui aprire le iscrizioni; non deve essere {@code null}
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return il {@link TournamentDto} rappresentante il torneo con le iscrizioni aperte
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se lo stato del torneo non consente l'apertura delle iscrizioni
     * @see #open(TournamentId)
     */
    TournamentDto open(TournamentId tournamentId, String originatingRequestId);

    /**
     * Apre le iscrizioni del torneo tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param tournamentId l'identificativo del torneo di cui aprire le iscrizioni; non deve essere {@code null}
     * @return il {@link TournamentDto} rappresentante il torneo con le iscrizioni aperte
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se lo stato del torneo non consente l'apertura delle iscrizioni
     * @see #open(TournamentId, String)
     */
    default TournamentDto open(TournamentId tournamentId) {
        return open(tournamentId, null);
    }
}