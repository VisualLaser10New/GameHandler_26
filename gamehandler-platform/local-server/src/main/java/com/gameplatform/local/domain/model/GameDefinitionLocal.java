package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class GameDefinitionLocal {
    private final GameType gameType;
    private final String name;
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean teamAllowed;
    private final Map<String, Object> registrationRules;
    private final Instant updatedAt;

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

    public GameType getGameType() { return gameType; }
    public String getName() { return name; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean isTeamAllowed() { return teamAllowed; }
    public Map<String, Object> getRegistrationRules() { return registrationRules; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameDefinitionLocal that = (GameDefinitionLocal) o;
        return Objects.equals(gameType, that.gameType);
    }
    @Override
    public int hashCode() { return Objects.hash(gameType); }
}
