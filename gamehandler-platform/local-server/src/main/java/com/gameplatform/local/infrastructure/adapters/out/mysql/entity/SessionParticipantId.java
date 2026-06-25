package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

public class SessionParticipantId implements Serializable {
    private String sessionId;
    private String userId;

    public SessionParticipantId() {
    }

    public SessionParticipantId(String sessionId, String userId) {
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
        SessionParticipantId that = (SessionParticipantId) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}
