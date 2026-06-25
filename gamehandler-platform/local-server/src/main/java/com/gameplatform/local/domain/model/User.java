package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;

public class User {
    private final UserId userId;
    private final String username;
    private final String passwordHash;
    private final List<String> roles;
    private final Instant syncedAt;

    public User(UserId userId, String username, String passwordHash, List<String> roles, Instant syncedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("PasswordHash cannot be null or empty");
        }
        if (roles == null) {
            throw new IllegalArgumentException("Roles cannot be null");
        }
        if (syncedAt == null) {
            throw new IllegalArgumentException("SyncedAt cannot be null");
        }
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = List.copyOf(roles);
        this.syncedAt = syncedAt;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}

