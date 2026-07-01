package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;

public class LocalSignupUser {
    private final UserId userId;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final List<String> roles;
    private final Instant createdAt;

    public LocalSignupUser(UserId userId, String username, String passwordHash, String email, List<String> roles, Instant createdAt) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("PasswordHash cannot be null or empty");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (roles == null) {
            throw new IllegalArgumentException("Roles cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = List.copyOf(roles);
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
