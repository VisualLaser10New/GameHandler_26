package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per le classifiche dei tornei.
 *
 * <p>Gestisce il salvataggio, la consultazione e la rimozione delle posizioni in
 * classifica dei partecipanti, incluse varianti con lock per aggiornamenti
 * concorrenti all'interno di una transazione.</p>
 *
 * @see TournamentStanding
 * @see TournamentId
 */
public interface TournamentStandingRepository {

    /**
     * Salva o aggiorna la classifica di torneo fornita.
     *
     * @param standing la classifica da persistere; non deve essere {@code null}
     * @return la classifica salvata, eventualmente arricchita di metadati di persistenza
     * @throws IllegalArgumentException se {@code standing} è {@code null}
     */
    TournamentStanding save(TournamentStanding standing);

    /**
     * Restituisce la classifica per la coppia torneo e id partecipante indicata.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param participantId l'identificativo del partecipante; non deve essere {@code null}
     * @return un {@link Optional} contenente la classifica trovata, o vuoto se assente
     * @throws IllegalArgumentException se {@code tournamentId} o {@code participantId} sono {@code null}
     */
    Optional<TournamentStanding> findByTournamentAndParticipantId(TournamentId tournamentId, String participantId);

    /**
     * Restituisce tutte le classifiche del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista delle classifiche del torneo; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    List<TournamentStanding> findByTournament(TournamentId tournamentId);

    /**
     * Restituisce tutte le classifiche del torneo indicato acquisendo un lock per
     * aggiornamento, all'interno della transazione attiva.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista delle classifiche del torneo, bloccate per aggiornamento; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     * @throws IllegalStateException    se non è attiva alcuna transazione
     */
    List<TournamentStanding> findByTournamentIdForUpdate(TournamentId tournamentId);

    /**
     * Elimina la classifica identificata dalla coppia torneo e id partecipante, se presente.
     *
     * <p>Se l'associazione non esiste, l'operazione non ha effetto.</p>
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param participantId l'identificativo del partecipante; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code tournamentId} o {@code participantId} sono {@code null}
     */
    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}
