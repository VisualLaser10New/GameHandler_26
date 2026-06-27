package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class User {
    private UserId id;
    private String username;
    private String passwordHash;
    private String email;
    private List<String> roles;
    private Instant createdAt;

    public User(UserId id, String username, String passwordHash, String email, List<String> roles, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null, empty or blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null, empty or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null, empty or blank");
        }
        if (roles == null) {
            throw new IllegalArgumentException("Roles cannot be null");
        }
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Role cannot be null, empty or blank");
            }
        }
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = List.copyOf(roles);
        this.createdAt = createdAt;
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null, empty or blank");
        }
        this.passwordHash = newPasswordHash;
    }

    public void updateRoles(List<String> newRoles) {
        if (newRoles == null) {
            throw new IllegalArgumentException("Roles cannot be null");
        }
        for (String role : newRoles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Role cannot be null, empty or blank");
            }
        }
        this.roles = List.copyOf(newRoles);
    }

    public UserId getId() {
        return id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

