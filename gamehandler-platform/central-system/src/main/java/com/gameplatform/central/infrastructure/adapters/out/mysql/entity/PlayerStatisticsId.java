package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link PlayerStatisticsJpaEntity}, matching
 * the PIANO SQL {@code PRIMARY KEY (user_id, game_type)} (FASE 3, &sect;2.3).
 *
 * <p>Field names MUST match the entity's {@code @Id} field names so Hibernate
 * can populate them via reflection (precedent: {@code LocalAdminBuildingId}).</p>
 */
public class PlayerStatisticsId implements Serializable {
    private String userId;
    private String gameType;

    public PlayerStatisticsId() {
    }

    public PlayerStatisticsId(String userId, String gameType) {
        this.userId = userId;
        this.gameType = gameType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStatisticsId that = (PlayerStatisticsId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(gameType, that.gameType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, gameType);
    }
}