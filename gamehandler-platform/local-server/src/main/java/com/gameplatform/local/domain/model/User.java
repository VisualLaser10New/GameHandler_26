package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Modello del dominio che rappresenta un utente locale, contenente
 * le credenziali, i ruoli e le informazioni di contatto. POJO immutabile,
 * identità basata su {@link UserId}.
 *
 * @see UserId
 * @see LocalSignupUser
 */
public class User {
    private final UserId userId;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final List<String> roles;
    private final Instant eventTime;
    private final Instant updatedAt;

    /**
     * Costruisce un nuovo utente senza email, con eventTime come updatedAt.
     *
     * @param userId       identificatore dell'utente (non null)
     * @param username     nome utente (non blank)
     * @param passwordHash hash della password (non blank)
     * @param roles        lista dei ruoli (non null)
     * @param eventTime    istante dell'evento di creazione (non null)
     */
    public User(UserId userId, String username, String passwordHash, List<String> roles, Instant eventTime) {
        this(userId, username, passwordHash, null, roles, eventTime, eventTime);
    }

    /**
     * Costruisce un nuovo utente con email, con eventTime come updatedAt.
     *
     * @param userId       identificatore dell'utente (non null)
     * @param username     nome utente (non blank)
     * @param passwordHash hash della password (non blank)
     * @param email        indirizzo email (può essere null)
     * @param roles        lista dei ruoli (non null)
     * @param eventTime    istante dell'evento di creazione (non null)
     */
    public User(UserId userId, String username, String passwordHash, String email, List<String> roles, Instant eventTime) {
        this(userId, username, passwordHash, email, roles, eventTime, eventTime);
    }

    /**
     * Costruttore primario che inizializza tutti i campi dell'utente.
     *
     * @param userId       identificatore dell'utente (non null)
     * @param username     nome utente (non blank)
     * @param passwordHash hash della password (non blank)
     * @param email        indirizzo email (può essere null)
     * @param roles        lista dei ruoli (non null)
     * @param eventTime    istante dell'evento (non null)
     * @param updatedAt    istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se userId, username o passwordHash sono null/blank,
     *                                  o se roles, eventTime o updatedAt sono null
     */
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
     * @return email, o null se non specificato
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
     * Restituisce l'istante dell'evento associato.
     *
     * @return eventTime
     */
    public Instant getEventTime() {
        return eventTime;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Restituisce l'istante dell'evento. Metodo deprecato, utilizzare {@link #getEventTime()}.
     *
     * @return eventTime
     * @deprecated sostituito da {@link #getEventTime()}
     */
    @Deprecated
    public Instant getSyncedAt() {
        return eventTime;
    }
}
