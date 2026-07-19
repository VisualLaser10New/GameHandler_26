package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity per la tabella {@code game_definitions_local}.
 * Rappresenta la definizione di un tipo gioco disponibile sulla piattaforma,
 * comprensiva di configurazione dei giocatori, supporto squadre e regole
 * di registrazione (serializzate in JSON).
 *
 * @see GameJpaEntity
 */
@Entity
@Table(name = "game_definitions_local")
public class GameDefinitionLocalJpaEntity {
    @Id
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    @Column(name = "min_players", nullable = false)
    private Integer minPlayers;
    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;
    @Column(name = "team_allowed", nullable = false)
    private Boolean teamAllowed;
    @Column(name = "registration_rules", columnDefinition = "json")
    private String registrationRulesJson;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public GameDefinitionLocalJpaEntity() {
    }

    /**
     * Costruisce una nuova definizione di gioco con tutti i campi.
     *
     * @param gameType             identificativo del tipo di gioco
     * @param name                 nome visualizzato del gioco
     * @param minPlayers           numero minimo di giocatori
     * @param maxPlayers           numero massimo di giocatori
     * @param teamAllowed          indica se il gioco supporta le squadre
     * @param registrationRulesJson regole di registrazione in formato JSON
     * @param updatedAt            istante dell'ultimo aggiornamento
     */
    public GameDefinitionLocalJpaEntity(String gameType, String name, Integer minPlayers, Integer maxPlayers,
                                        Boolean teamAllowed, String registrationRulesJson, Instant updatedAt) {
        this.gameType = gameType;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamAllowed = teamAllowed;
        this.registrationRulesJson = registrationRulesJson;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificativo del tipo di gioco.
     *
     * @return gameType
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta l'identificativo del tipo di gioco.
     *
     * @param gameType nuovo tipo di gioco
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce il nome visualizzato del gioco.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Imposta il nome visualizzato del gioco.
     *
     * @param name nuovo nome del gioco
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Restituisce il numero minimo di giocatori.
     *
     * @return minPlayers
     */
    public Integer getMinPlayers() {
        return minPlayers;
    }

    /**
     * Imposta il numero minimo di giocatori.
     *
     * @param minPlayers nuovo numero minimo
     */
    public void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    /**
     * Restituisce il numero massimo di giocatori.
     *
     * @return maxPlayers
     */
    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Imposta il numero massimo di giocatori.
     *
     * @param maxPlayers nuovo numero massimo
     */
    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * Indica se il gioco supporta le squadre.
     *
     * @return {@code true} se le squadre sono consentite
     */
    public Boolean getTeamAllowed() {
        return teamAllowed;
    }

    /**
     * Imposta il supporto squadre per il gioco.
     *
     * @param teamAllowed {@code true} per consentire squadre
     */
    public void setTeamAllowed(Boolean teamAllowed) {
        this.teamAllowed = teamAllowed;
    }

    /**
     * Restituisce le regole di registrazione in formato JSON.
     *
     * @return registrationRulesJson (può essere {@code null})
     */
    public String getRegistrationRulesJson() {
        return registrationRulesJson;
    }

    /**
     * Imposta le regole di registrazione in formato JSON.
     *
     * @param registrationRulesJson nuove regole di registrazione
     */
    public void setRegistrationRulesJson(String registrationRulesJson) {
        this.registrationRulesJson = registrationRulesJson;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Imposta l'istante dell'ultimo aggiornamento.
     *
     * @param updatedAt nuovo istante di aggiornamento
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}