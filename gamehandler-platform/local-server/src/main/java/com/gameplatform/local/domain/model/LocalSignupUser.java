package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Rappresenta i dati di registrazione di un utente locale, contenenti
 * le credenziali, i ruoli e le informazioni di contatto. POJO immutabile
 * utilizzato per il flusso di iscrizione iniziale.
 *
 * @see User
 */
public class LocalSignupUser {
    private final UserId userId;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final List<String> roles;
    private final Instant createdAt;

    /**
     * Costruisce un nuovo utente registrato localmente.
     *
     * @param userId       identificatore dell'utente (non null)
     * @param username     nome utente (non blank)
     * @param passwordHash hash della password (non blank)
     * @param email        indirizzo email (non blank)
     * @param roles        lista dei ruoli (non null, viene copiata difensivamente)
     * @param createdAt    istante di creazione (non null)
     * @throws IllegalArgumentException se userId, username, passwordHash, email sono null/blank,
     *                                  o se roles o createdAt sono null
     */
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

    /**
     * Restituisce l'identificatore dell'utente.
     *
     * @return userId
     */
    public UserId getUserId() {
        return userId;
    }

    /**
     * Restituisce il nome utente.
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Restituisce l'hash della password.
     *
     * @return passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Restituisce l'indirizzo email.
     *
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Restituisce la lista dei ruoli.
     *
     * @return roles
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Restituisce l'istante di creazione.
     *
     * @return createdAt
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
