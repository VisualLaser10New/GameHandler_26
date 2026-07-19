package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;

import java.time.Instant;
import java.util.List;

/**
 * Use case per la richiesta di creazione di un nuovo torneo da parte
 * di un amministratore di piattaforma. Effettua il pre-controllo del
 * ruolo {@code PLATFORM_ADMIN} sugli utenti replicati, quindi scrive
 * in modo atomico una riga {@code PENDING} su {@code admin_requests_local}
 * e l'evento di outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface CreateTournamentRequestedUseCase {

    /**
     * Avanza la richiesta di creazione di un torneo con i parametri specificati.
     *
     * @param name         nome del torneo
     * @param gameType     tipo di gioco del torneo
     * @param teamBased    indica se il torneo &egrave; a squadre
     * @param teamSize     dimensione di ciascuna squadra
     * @param startsAt     data e ora di inizio del torneo
     * @param buildingIds  elenco delle strutture coinvolte
     * @param actingUserId identificativo dell'amministratore richiedente
     * @param actingRole   ruolo con cui l'amministratore agisce
     * @param buildingId   identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto create(String name,
                            GameType gameType,
                            boolean teamBased,
                            int teamSize,
                            Instant startsAt,
                            List<String> buildingIds,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}