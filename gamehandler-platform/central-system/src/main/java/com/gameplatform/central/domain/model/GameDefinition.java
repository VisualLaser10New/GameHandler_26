package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta la definizione configurabile di un tipo di
 * gioco. Contiene i parametri invarianti del gioco, quali i limiti sul numero di
 * giocatori, la possibilità di formare squadre e le regole di registrazione.
 * L'identità dell'entità è determinata dal tipo di gioco: esiste una sola
 * definizione per ciascun {@link GameType}.
 *
 * @see GameType
 */
public class GameDefinition {
    private final GameType gameType;
    private final String name;
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean teamAllowed;
    private final Map<String, Object> registrationRules;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Costruisce una definizione di gioco con i parametri specificati.
     *
     * @param gameType tipo di gioco che identifica la definizione; non può essere {@code null}
     * @param name nome descrittivo del gioco; non può essere {@code null} né vuoto
     * @param minPlayers numero minimo di giocatori; deve essere maggiore o uguale a 1 e non superiore a {@code maxPlayers}
     * @param maxPlayers numero massimo di giocatori; deve essere maggiore o uguale a 1
     * @param teamAllowed indica se il gioco consente la formazione di squadre
     * @param registrationRules regole di registrazione aggiuntive; se {@code null} viene mantenuta {@code null}, altrimenti ne viene creata una copia immutabile
     * @param createdAt istante di creazione della definizione; non può essere {@code null}
     * @param updatedAt istante dell'ultimo aggiornamento della definizione; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public GameDefinition(GameType gameType, String name, int minPlayers, int maxPlayers,
                          boolean teamAllowed, Map<String, Object> registrationRules,
                          Instant createdAt, Instant updatedAt) {
        if (gameType == null) throw new IllegalArgumentException("GameType cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (minPlayers < 1) throw new IllegalArgumentException("minPlayers must be >= 1");
        if (maxPlayers < 1) throw new IllegalArgumentException("maxPlayers must be >= 1");
        if (minPlayers > maxPlayers) throw new IllegalArgumentException("minPlayers must be <= maxPlayers");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt cannot be null");
        this.gameType = gameType;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamAllowed = teamAllowed;
        this.registrationRules = registrationRules == null ? null : Map.copyOf(registrationRules);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce il tipo di gioco che identifica la definizione.
     *
     * @return il tipo di gioco, mai {@code null}
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce il nome descrittivo del gioco.
     *
     * @return il nome del gioco, mai {@code null} né vuoto
     */
    public String getName() {
        return name;
    }

    /**
     * Restituisce il numero minimo di giocatori consentito.
     *
     * @return il numero minimo di giocatori, sempre maggiore o uguale a 1
     */
    public int getMinPlayers() {
        return minPlayers;
    }

    /**
     * Restituisce il numero massimo di giocatori consentito.
     *
     * @return il numero massimo di giocatori, sempre maggiore o uguale a 1
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Indica se il gioco consente la formazione di squadre.
     *
     * @return {@code true} se sono ammesse le squadre, {@code false} altrimenti
     */
    public boolean isTeamAllowed() {
        return teamAllowed;
    }

    /**
     * Restituisce le regole di registrazione aggiuntive del gioco.
     *
     * @return una mappa immutabile con le regole di registrazione, oppure {@code null} se non definite
     */
    public Map<String, Object> getRegistrationRules() {
        return registrationRules;
    }

    /**
     * Restituisce l'istante di creazione della definizione.
     *
     * @return l'istante di creazione, mai {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento della definizione.
     *
     * @return l'istante dell'ultimo aggiornamento, mai {@code null}
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Confronta questa definizione con un altro oggetto verificandone
     * l'uguaglianza sulla base del solo tipo di gioco.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è una {@code GameDefinition} con lo stesso tipo di gioco, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameDefinition that = (GameDefinition) o;
        return Objects.equals(gameType, that.gameType);
    }

    /**
     * Restituisce il codice hash calcolato sul tipo di gioco.
     *
     * @return il codice hash della definizione
     */
    @Override
    public int hashCode() {
        return Objects.hash(gameType);
    }
}
