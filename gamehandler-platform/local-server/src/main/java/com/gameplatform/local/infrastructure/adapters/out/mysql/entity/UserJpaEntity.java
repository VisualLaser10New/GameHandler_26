package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "replicated_users")
public class UserJpaEntity {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "roles", length = 255)
    private String roles;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public UserJpaEntity() {
    }

    public UserJpaEntity(String userId, String username, String passwordHash, String roles, Instant syncedAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.syncedAt = syncedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
