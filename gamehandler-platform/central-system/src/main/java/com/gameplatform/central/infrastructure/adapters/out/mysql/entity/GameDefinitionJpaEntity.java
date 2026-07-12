package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code game_definitions} Source-of-Truth table (FASE 2,
 * GAME_ADMIN). PK is the business key {@code game_type} (stored as the
 * {@link com.gameplatform.shared.domain.model.GameType} enum NAME), so no
 * {@code @GeneratedValue}. No JPA relations are declared — the JSON
 * {@code registration_rules} column is held as a plain {@code String} and
 * converted Map&harr;JSON in {@code GameDefinitionMapper}, per RNF-08.
 */
@Entity
@Table(name = "game_definitions")
public class GameDefinitionJpaEntity {

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameDefinitionJpaEntity() {
    }

    public GameDefinitionJpaEntity(String gameType, String name, Integer minPlayers, Integer maxPlayers,
                                   Boolean teamAllowed, String registrationRulesJson,
                                   Instant createdAt, Instant updatedAt) {
        this.gameType = gameType;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamAllowed = teamAllowed;
        this.registrationRulesJson = registrationRulesJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Boolean getTeamAllowed() {
        return teamAllowed;
    }

    public void setTeamAllowed(Boolean teamAllowed) {
        this.teamAllowed = teamAllowed;
    }

    public String getRegistrationRulesJson() {
        return registrationRulesJson;
    }

    public void setRegistrationRulesJson(String registrationRulesJson) {
        this.registrationRulesJson = registrationRulesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
