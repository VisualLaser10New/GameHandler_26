package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import java.util.List;

/**
 * Caso d'uso di lettura che restituisce l'elenco dei partecipanti
 * iscritti a un torneo.
 */
public interface ListTournamentParticipantsUseCase {

    /**
     * Restituisce i partecipanti registrati per il torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo di cui elencare i partecipanti; non deve essere {@code null}
     * @return la lista di {@link TournamentParticipantDto} rappresentante i partecipanti; la lista è vuota se il torneo non ha iscrizioni
     */
    List<TournamentParticipantDto> listParticipants(TournamentId tournamentId);
}
