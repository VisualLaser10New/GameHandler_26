package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;

public class User {
    private final UserId userId;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final List<String> roles;
    private final Instant eventTime;
    private final Instant updatedAt;

    public User(UserId userId, String username, String passwordHash, List<String> roles, Instant eventTime) {
        this(userId, username, passwordHash, null, roles, eventTime, eventTime);
    }

    public User(UserId userId, String username, String passwordHash, String email, List<String> roles, Instant eventTime) {
        this(userId, username, passwordHash, email, roles, eventTime, eventTime);
    }

    public User(UserId userId, String username, String passwordHash, String email, List<String> roles,
                Instant eventTime, Instant updatedAt) {
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
        if (eventTime == null) {
            throw new IllegalArgumentException("EventTime cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("UpdatedAt cannot be null");
        }
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = List.copyOf(roles);
        this.eventTime = eventTime;
        this.updatedAt = updatedAt;
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

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Deprecated
    public Instant getSyncedAt() {
        return eventTime;
    }
}
