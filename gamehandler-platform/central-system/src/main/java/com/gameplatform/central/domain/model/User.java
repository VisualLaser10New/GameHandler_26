package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;

public class User {
    private UserId id;
    private String username;
    private String passwordHash;
    private String email;
    private List<String> roles;
    private Instant createdAt;

    public User(UserId id, String username, String passwordHash, String email, List<String> roles, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    void updateRoles(List<String> newRoles) {
        this.roles = newRoles;
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
}
