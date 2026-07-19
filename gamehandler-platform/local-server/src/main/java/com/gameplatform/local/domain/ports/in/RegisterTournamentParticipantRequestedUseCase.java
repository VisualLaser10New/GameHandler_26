package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Use case per la richiesta di registrazione di un partecipante a un torneo.
 * Un giocatore si registra come partecipante individuale o come capitano
 * di una squadra. Effettua il pre-controllo del ruolo {@code PLAYER} sugli
 * utenti replicati, quindi scrive in modo atomico una riga {@code PENDING}
 * su {@code admin_requests_local} e l'evento di outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface RegisterTournamentParticipantRequestedUseCase {

    /**
     * Avanza la richiesta di registrazione di un partecipante al torneo specificato.
     *
     * @param tournamentId  identificativo del torneo
     * @param actingUserId  identificativo dell'utente che si registra
     * @param actingRole    ruolo con cui l'utente agisce
     * @param buildingId    identificativo della struttura di appartenenza
     * @param teamName      nome della squadra, oppure null per partecipazione individuale
     * @param teamMemberIds elenco degli identificativi dei membri della squadra
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto register(String tournamentId,
                              String actingUserId,
                              String actingRole,
                              String buildingId,
                              String teamName,
                              java.util.List<String> teamMemberIds);
}