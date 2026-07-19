package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.time.Instant;
import java.util.Map;

/**
 * DTO che rappresenta un evento di definizione di un gioco scambiato tra i componenti della piattaforma.
 *
 * <p>Contiene i dati descrittivi e di configurazione di un gioco, quali tipologia, parametri di
 * partecipazione e regole di registrazione, utilizzati per propagare le variazioni di definizione
 * attraverso il sistema.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record GameDefinitionEventDto(
        String eventId,
        String eventType,
        GameType gameType,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules,
        Instant updatedAt,
        String originatingRequestId
) {
    /**
     * Costruisce un evento di definizione gioco senza identificativo della richiesta origine.
     *
     * <p>Invoca il costruttore canonico impostando {@code originatingRequestId} a {@code null}.</p>
     *
     * @param eventId      identificativo univoco dell'evento; non deve essere {@code null} né vuoto
     * @param eventType    tipologia di evento; non deve essere {@code null} né vuoto
     * @param gameType     tipologia di gioco di appartenenza; non deve essere {@code null}
     * @param name         nome del gioco; non deve essere {@code null} né vuoto
     * @param minPlayers   numero minimo di giocatori; deve essere strettamente positivo e non superiore a {@code maxPlayers}
     * @param maxPlayers   numero massimo di giocatori; deve essere strettamente positivo e non inferiore a {@code minPlayers}
     * @param teamAllowed  {@code true} se la formazione di squadre è consentita, {@code false} altrimenti
     * @param registrationRules regole di registrazione associate al gioco; può essere {@code null} o una mappa vuota
     * @param updatedAt    istante di aggiornamento della definizione; non deve essere {@code null}
     *
     * @see #GameDefinitionEventDto(String, String, GameType, String, int, int, boolean, Map, Instant, String)
     */
    public GameDefinitionEventDto(String eventId, String eventType, GameType gameType, String name,
                                  int minPlayers, int maxPlayers, boolean teamAllowed,
                                  Map<String, Object> registrationRules, Instant updatedAt) {
        this(eventId, eventType, gameType, name, minPlayers, maxPlayers, teamAllowed,
                registrationRules, updatedAt, null);
    }
}