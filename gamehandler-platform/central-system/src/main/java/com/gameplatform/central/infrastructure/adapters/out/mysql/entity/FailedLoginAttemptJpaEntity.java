package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "failed_login_attempts", indexes = {
    @Index(name = "idx_failed_login_username_time", columnList = "username, attempt_time")
})
public class FailedLoginAttemptJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "attempt_time", nullable = false)
    private Instant attemptTime;

    public FailedLoginAttemptJpaEntity() {
    }

    public FailedLoginAttemptJpaEntity(String id, String username, Instant attemptTime) {
        this.id = id;
        this.username = username;
        this.attemptTime = attemptTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getAttemptTime() {
        return attemptTime;
    }

    public void setAttemptTime(Instant attemptTime) {
        this.attemptTime = attemptTime;
    }
}
