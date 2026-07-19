package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.Map;

/**
 * Use case per la richiesta di creazione o aggiornamento di una definizione
 * di gioco da parte di un amministratore dei giochi. Effettua il pre-controllo
 * del ruolo {@code GAME_ADMIN} sugli utenti replicati, quindi scrive in modo
 * atomico una riga {@code PENDING} su {@code admin_requests_local} e l'evento
 * di outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface UpsertGameDefinitionRequestedUseCase {

    /**
     * Avanza la richiesta di creazione o aggiornamento di una definizione di gioco.
     *
     * @param gameType          tipo di gioco
     * @param name              nome della definizione del gioco
     * @param minPlayers        numero minimo di giocatori
     * @param maxPlayers        numero massimo di giocatori
     * @param teamAllowed       indica se il gioco permette squadre
     * @param registrationRules regole di registrazione come mappa chiave-valore
     * @param actingUserId      identificativo dell'amministratore richiedente
     * @param actingRole        ruolo con cui l'amministratore agisce
     * @param buildingId        identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto upsert(GameType gameType,
                            String name,
                            int minPlayers,
                            int maxPlayers,
                            boolean teamAllowed,
                            Map<String, Object> registrationRules,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}