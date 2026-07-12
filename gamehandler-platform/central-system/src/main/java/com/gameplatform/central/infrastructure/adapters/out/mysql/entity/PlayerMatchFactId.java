package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link PlayerMatchFactJpaEntity}, matching the
 * PIANO SQL {@code PRIMARY KEY (session_id, user_id)} (FASE 3, &sect;2.3).
 *
 * <p>Field names MUST match the entity's {@code @Id} field names so Hibernate
 * can populate them via reflection (precedent: {@code LocalAdminBuildingId}).</p>
 */
public class PlayerMatchFactId implements Serializable {
    private String sessionId;
    private String userId;

    public PlayerMatchFactId() {
    }

    public PlayerMatchFactId(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerMatchFactId that = (PlayerMatchFactId) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}