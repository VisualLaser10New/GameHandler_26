package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Caso d'uso che rimuove l'iscrizione di un utente dalla propria
 * partecipazione a un torneo.
 */
public interface UnregisterTournamentParticipantUseCase {

    /**
     * Annulla l'iscrizione dell'utente corrente al torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo da cui disiscriversi; non deve essere {@code null}
     * @param currentUserId l'identificativo dell'utente che richiede la disiscrizione; non deve essere {@code null}
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.ParticipantNotFoundException se l'utente non risulta iscritto al torneo
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se lo stato del torneo non consente la disiscrizione
     */
    void unregister(TournamentId tournamentId, UserId currentUserId);
}
