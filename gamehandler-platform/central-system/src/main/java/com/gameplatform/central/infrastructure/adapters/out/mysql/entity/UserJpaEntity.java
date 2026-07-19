package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code users} del database MySQL.
 *
 * <p>Rappresenta un utente del sistema centrale, con le credenziali e i ruoli
 * associati. Lo username e l'email sono univoci. La password è memorizzata come
 * hash e non come testo in chiaro. I ruoli sono mantenuti in una singola colonna
 * testuale secondo la convenzione adottata nel progetto. Non sono dichiarate
 * relazioni JPA: i riferimenti ad altre entità sono gestiti tramite
 * identificativi testuali.</p>
 *
 * @see LocalAdminBuildingJpaEntity
 * @see FailedLoginAttemptJpaEntity
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "roles", nullable = false, length = 1024)
    private String roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public UserJpaEntity() {
    }

    /**
     * Costruisce un utente con i dati anagrafici e di sicurezza forniti.
     *
     * @param id identificativo univoco dell'utente; non deve essere {@code null}
     * @param username nome utente univoco; non deve essere {@code null}
     * @param passwordHash hash della password dell'utente; non deve essere {@code null}
     * @param email indirizzo email univoco dell'utente; non deve essere {@code null}
     * @param roles ruoli associati all'utente, in formato testuale; non deve essere {@code null}
     * @param createdAt istante di creazione dell'utente; non deve essere {@code null}
     */
    public UserJpaEntity(String id, String username, String passwordHash, String email, String roles, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificativo univoco dell'utente.
     *
     * @return l'identificativo dell'utente; non deve essere {@code null}
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificativo univoco dell'utente.
     *
     * @param id nuovo identificativo dell'utente; può essere {@code null}
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce il nome utente.
     *
     * @return il nome utente; non deve essere {@code null}
     */
    public String getUsername() {
        return username;
    }

    /**
     * Imposta il nome utente.
     *
     * @param username nuovo nome utente; non deve essere {@code null}
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Restituisce l'hash della password dell'utente.
     *
     * @return l'hash della password; non deve essere {@code null}
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Imposta l'hash della password dell'utente.
     *
     * @param passwordHash nuovo hash della password; non deve essere {@code null}
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Restituisce l'indirizzo email dell'utente.
     *
     * @return l'indirizzo email; non deve essere {@code null}
     */
    public String getEmail() {
        return email;
    }

    /**
     * Imposta l'indirizzo email dell'utente.
     *
     * @param email nuovo indirizzo email; non deve essere {@code null}
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Restituisce i ruoli associati all'utente.
     *
     * @return i ruoli dell'utente in formato testuale; non devono essere {@code null}
     */
    public String getRoles() {
        return roles;
    }

    /**
     * Imposta i ruoli associati all'utente.
     *
     * @param roles nuovi ruoli dell'utente in formato testuale; non devono essere {@code null}
     */
    public void setRoles(String roles) {
        this.roles = roles;
    }

    /**
     * Restituisce l'istante di creazione dell'utente.
     *
     * @return l'istante di creazione; non deve essere {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione dell'utente.
     *
     * @param createdAt nuovo istante di creazione; non deve essere {@code null}
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
