package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Replica locale di sola lettura della definizione di un gioco, contenente
 * le regole di registrazione e i vincoli sul numero di giocatori. POJO
 * immutabile, identità basata su {@link GameType}.
 *
 * @see GameType
 */
public class GameDefinitionLocal {
    private final GameType gameType;
    private final String name;
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean teamAllowed;
    private final Map<String, Object> registrationRules;
    private final Instant updatedAt;

    /**
     * Costruisce una nuova definizione di gioco locale.
     *
     * @param gameType          tipo di gioco (non null)
     * @param name              nome della definizione (non blank)
     * @param minPlayers        numero minimo di giocatori (>= 1)
     * @param maxPlayers        numero massimo di giocatori (>= 1, >= minPlayers)
     * @param teamAllowed       indica se sono consentite squadre
     * @param registrationRules mappa delle regole di registrazione (può essere null)
     * @param updatedAt         istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se gameType è null, name è blank,
     *                                  minPlayers < 1, maxPlayers < 1,
     *                                  minPlayers > maxPlayers, o updatedAt è null
     */
    public GameDefinitionLocal(GameType gameType, String name, int minPlayers, int maxPlayers, boolean teamAllowed, Map<String, Object> registrationRules, Instant updatedAt) {
        if (gameType == null) throw new IllegalArgumentException("GameType cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (minPlayers < 1) throw new IllegalArgumentException("minPlayers must be >= 1");
        if (maxPlayers < 1) throw new IllegalArgumentException("maxPlayers must be >= 1");
        if (minPlayers > maxPlayers) throw new IllegalArgumentException("minPlayers must be <= maxPlayers");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt cannot be null");
        this.gameType = gameType;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamAllowed = teamAllowed;
        this.registrationRules = registrationRules != null ? Map.copyOf(registrationRules) : null;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce il tipo di gioco.
     *
     * @return gameType
     */
    public GameType getGameType() { return gameType; }

    /**
     * Restituisce il nome della definizione.
     *
     * @return name
     */
    public String getName() { return name; }

    /**
     * Restituisce il numero minimo di giocatori.
     *
     * @return minPlayers
     */
    public int getMinPlayers() { return minPlayers; }

    /**
     * Restituisce il numero massimo di giocatori.
     *
     * @return maxPlayers
     */
    public int getMaxPlayers() { return maxPlayers; }

    /**
     * Indica se sono consentite squadre.
     *
     * @return true se le squadre sono ammesse
     */
    public boolean isTeamAllowed() { return teamAllowed; }

    /**
     * Restituisce la mappa delle regole di registrazione.
     *
     * @return registrationRules, o null se non specificata
     */
    public Map<String, Object> getRegistrationRules() { return registrationRules; }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Confronta questa definizione con un altro oggetto per uguaglianza basata su gameType.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameDefinitionLocal that = (GameDefinitionLocal) o;
        return Objects.equals(gameType, that.gameType);
    }

    /**
     * Restituisce l'hash code basato su gameType.
     *
     * @return hash code
     */
    @Override
    public int hashCode() { return Objects.hash(gameType); }
}
