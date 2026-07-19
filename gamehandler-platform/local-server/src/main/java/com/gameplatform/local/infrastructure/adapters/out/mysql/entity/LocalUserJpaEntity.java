package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity per la tabella {@code users}.
 * Rappresenta un utente locale registrato sulla piattaforma, con credenziali
 * di accesso (username e password hash), email e ruoli associati.
 *
 * @see UserJpaEntity
 */
@Entity
@Table(name = "users")
public class LocalUserJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "roles", length = 255)
    private String roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public LocalUserJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza di utente locale con tutti i campi.
     *
     * @param id           identificatore univoco dell'utente
     * @param username     nome utente (unique)
     * @param passwordHash hash della password
     * @param email        indirizzo email (opzionale)
     * @param roles        ruoli associati all'utente (opzionale)
     * @param createdAt    istante di creazione dell'utente
     */
    public LocalUserJpaEntity(String id, String username, String passwordHash, String email, String roles, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificatore univoco dell'utente.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco dell'utente.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
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
     * Imposta il nome utente.
     *
     * @param username nuovo nome utente
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Restituisce l'hash della password dell'utente.
     *
     * @return passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Imposta l'hash della password dell'utente.
     *
     * @param passwordHash nuovo hash password
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Restituisce l'indirizzo email dell'utente.
     *
     * @return email (può essere {@code null})
     */
    public String getEmail() {
        return email;
    }

    /**
     * Imposta l'indirizzo email dell'utente.
     *
     * @param email nuovo indirizzo email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Restituisce i ruoli associati all'utente.
     *
     * @return roles (può essere {@code null})
     */
    public String getRoles() {
        return roles;
    }

    /**
     * Imposta i ruoli associati all'utente.
     *
     * @param roles nuovi ruoli
     */
    public void setRoles(String roles) {
        this.roles = roles;
    }

    /**
     * Restituisce l'istante di creazione dell'utente.
     *
     * @return createdAt
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione dell'utente.
     *
     * @param createdAt nuovo istante di creazione
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
