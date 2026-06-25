package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_participants")
@IdClass(SessionParticipantId.class)
public class SessionParticipantJpaEntity {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    public SessionParticipantJpaEntity() {
    }

    public SessionParticipantJpaEntity(String sessionId, String userId) {
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
}
