package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain entity representing the configurable definition of a game type in the
 * central Source-of-Truth.
 *
 * <p>Holds the invariant parameters of a game (player count bounds, team policy,
 * registration rules) that are replicated to the Local Servers via the outbox
 * event {@code GAME_DEFINITION_UPSERTED}.</p>
 *
 * <p>Identity is the {@code gameType} (primary key): one definition per
 * {@link GameType}.</p>
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

    public GameType getGameType() {
        return gameType;
    }

    public String getName() {
        return name;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isTeamAllowed() {
        return teamAllowed;
    }

    public Map<String, Object> getRegistrationRules() {
        return registrationRules;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameDefinition that = (GameDefinition) o;
        return Objects.equals(gameType, that.gameType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameType);
    }
}
