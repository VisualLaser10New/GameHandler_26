package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import java.util.List;

/**
 * Caso d'uso per l'iscrizione di un team a un torneo.
 *
 * <p>Consente di registrare un team (capitano e membri) a un torneo
 * aperto, verificando che il torneo sia in stato di iscrizione aperta
 * e che non vi siano duplicati. Supporta sia chiamate con tracciabilità
 * tramite {@code originatingRequestId} sia chiamate REST dirette.</p>
 *
 * @see com.gameplatform.central.application.service.TournamentRegistrationService
 * @see com.gameplatform.shared.domain.exception.DuplicateTournamentParticipantException
 */
public interface RegisterTournamentParticipantUseCase {

    /**
     * Iscrive un team al torneo indicato con i membri e il capitano forniti.
     *
     * @param tournamentId l'identificativo del torneo a cui iscriversi; non deve essere {@code null}
     * @param captainId l'identificativo dell'utente capitano del team; non deve essere {@code null}
     * @param teamName il nome del team; non deve essere {@code null} né vuoto
     * @param teamMemberIds la lista degli identificativi dei membri del team; non deve essere {@code null}; se vuota il team è composto solo dal capitano
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return il {@link TournamentParticipantDto} rappresentante l'iscrizione appena creata
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se le iscrizioni non sono aperte
     * @throws com.gameplatform.shared.domain.exception.DuplicateParticipantException se il team o un membro è già iscritto
     * @see #register(TournamentId, UserId, String, List)
     */
    TournamentParticipantDto register(TournamentId tournamentId, UserId captainId, String teamName,
                                       List<String> teamMemberIds, String originatingRequestId);

    /**
     * Iscrive un team al torneo tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param tournamentId l'identificativo del torneo a cui iscriversi; non deve essere {@code null}
     * @param captainId l'identificativo dell'utente capitano del team; non deve essere {@code null}
     * @param teamName il nome del team; non deve essere {@code null} né vuoto
     * @param teamMemberIds la lista degli identificativi dei membri del team; non deve essere {@code null}
     * @return il {@link TournamentParticipantDto} rappresentante l'iscrizione appena creata
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se le iscrizioni non sono aperte
     * @throws com.gameplatform.shared.domain.exception.DuplicateParticipantException se il team o un membro è già iscritto
     * @see #register(TournamentId, UserId, String, List, String)
     */
    default TournamentParticipantDto register(TournamentId tournamentId, UserId captainId, String teamName,
                                               List<String> teamMemberIds) {
        return register(tournamentId, captainId, teamName, teamMemberIds, null);
    }
}