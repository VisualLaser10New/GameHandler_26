package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per i partecipanti ai tornei.
 *
 * <p>Gestisce l'iscrizione, la consultazione e la rimozione dei partecipanti a
 * un torneo, supportando le verifiche di esistenza e i conteggi necessari al
 * dominio dei tornei.</p>
 *
 * @see TournamentParticipant
 * @see TournamentId
 */
public interface TournamentParticipantRepository {

    /**
     * Salva o aggiorna il partecipante di torneo fornito.
     *
     * @param participant il partecipante da persistere; non deve essere {@code null}
     * @return il partecipante salvato, eventualmente arricchito di metadati di persistenza
     * @throws IllegalArgumentException se {@code participant} è {@code null}
     */
    TournamentParticipant save(TournamentParticipant participant);

    /**
     * Restituisce il partecipante identificato dalla coppia torneo e id partecipante.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param participantId l'identificativo del partecipante; non deve essere {@code null}
     * @return un {@link Optional} contenente il partecipante trovato, o vuoto se assente
     * @throws IllegalArgumentException se {@code tournamentId} o {@code participantId} sono {@code null}
     */
    Optional<TournamentParticipant> findByTournamentAndParticipantId(TournamentId tournamentId, String participantId);

    /**
     * Restituisce tutti i partecipanti del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista dei partecipanti del torneo; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    List<TournamentParticipant> findByTournament(TournamentId tournamentId);

    /**
     * Conta i partecipanti del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return il numero di partecipanti, sempre non negativo
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    long countByTournament(TournamentId tournamentId);

    /**
     * Verifica l'esistenza di un partecipante nel torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param participantId l'identificativo del partecipante; non deve essere {@code null}
     * @return {@code true} se il partecipante esiste, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code tournamentId} o {@code participantId} sono {@code null}
     */
    boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId);

    /**
     * Elimina il partecipante identificato dalla coppia torneo e id partecipante, se presente.
     *
     * <p>Se l'associazione non esiste, l'operazione non ha effetto.</p>
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @param participantId l'identificativo del partecipante; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code tournamentId} o {@code participantId} sono {@code null}
     */
    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}
