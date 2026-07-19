package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * JPA entity per la tabella {@code replicated_users}.
 * Rappresenta un utente replicato dal sistema Central, contenente le
 * credenziali, i ruoli e i timestamp di sincronizzazione. Utilizza
 * optimistic locking tramite {@code @Version}.
 *
 * @see LocalUserJpaEntity
 */
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

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "roles", length = 255)
    private String roles;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public UserJpaEntity() {
    }

    /**
     * Costruisce un utente replicato senza email.
     *
     * @param userId       identificativo dell'utente
     * @param username     nome utente
     * @param passwordHash hash della password
     * @param roles        ruoli associati
     * @param syncedAt     istante di sincronizzazione
     */
    public UserJpaEntity(String userId, String username, String passwordHash, String roles, Instant syncedAt) {
        this(userId, username, passwordHash, null, roles, syncedAt);
    }

    /**
     * Costruisce un utente replicato con email e stesso timestamp per eventTime e updatedAt.
     *
     * @param userId       identificativo dell'utente
     * @param username     nome utente
     * @param passwordHash hash della password
     * @param email        indirizzo email (può essere {@code null})
     * @param roles        ruoli associati
     * @param syncedAt     istante di sincronizzazione (usato anche per eventTime e updatedAt)
     */
    public UserJpaEntity(String userId, String username, String passwordHash, String email, String roles, Instant syncedAt) {
        this(userId, username, passwordHash, email, roles, syncedAt, syncedAt);
    }

    /**
     * Costruisce un utente replicato con tutti i campi inclusi tim distinti per eventTime e updatedAt.
     *
     * @param userId       identificativo dell'utente
     * @param username     nome utente
     * @param passwordHash hash della password
     * @param email        indirizzo email (può essere {@code null})
     * @param roles        ruoli associati
     * @param eventTime    istante dell'evento di sincronizzazione
     * @param updatedAt    istante dell'ultimo aggiornamento
     */
    public UserJpaEntity(String userId, String username, String passwordHash, String email, String roles,
                        Instant eventTime, Instant updatedAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = roles;
        this.syncedAt = eventTime;
        this.eventTime = eventTime;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificativo dell'utente.
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente.
     *
     * @param userId nuovo identificativo
     */
    public void setUserId(String userId) {
        this.userId = userId;
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
     * Restituisce l'hash della password.
     *
     * @return passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Imposta l'hash della password.
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
     * Restituisce l'istante di sincronizzazione.
     *
     * @return syncedAt
     */
    public Instant getSyncedAt() {
        return syncedAt;
    }

    /**
     * Imposta l'istante di sincronizzazione.
     *
     * @param syncedAt nuovo istante di sincronizzazione
     */
    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }

    /**
     * Restituisce l'istante dell'evento di sincronizzazione.
     *
     * @return eventTime
     */
    public Instant getEventTime() {
        return eventTime;
    }

    /**
     * Imposta l'istante dell'evento di sincronizzazione.
     *
     * @param eventTime nuovo istante evento
     */
    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
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
     * Imposta l'istante dell'ultimo aggiornamento.
     *
     * @param updatedAt nuovo istante di aggiornamento
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce la versione per l'optimistic locking.
     *
     * @return version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Imposta la versione per l'optimistic locking.
     *
     * @param version nuova versione
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
