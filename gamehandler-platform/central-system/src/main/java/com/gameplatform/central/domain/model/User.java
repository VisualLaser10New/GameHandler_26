package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta un utente della piattaforma, con le proprie
 * credenziali, l'indirizzo email e l'insieme dei ruoli assegnati. Consente la
 * modifica della password e l'aggiornamento dei ruoli. L'identità è determinata
 * dall'identificativo dell'utente.
 *
 * @see UserId
 */
public class User {
    private UserId id;
    private String username;
    private String passwordHash;
    private String email;
    private List<String> roles;
    private Instant createdAt;

    /**
     * Costruisce un utente con i valori specificati.
     *
     * @param id identificativo univoco dell'utente; non può essere {@code null}
     * @param username nome utente; non può essere {@code null} né vuoto
     * @param passwordHash hash della password; non può essere {@code null} né vuoto
     * @param email indirizzo email dell'utente; non può essere {@code null} né vuoto
     * @param roles elenco dei ruoli assegnati; non può essere {@code null} e nessun ruolo può essere {@code null} o vuoto
     * @param createdAt istante di creazione dell'utente; può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
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

    /**
     * Aggiorna l'hash della password dell'utente con il nuovo valore fornito.
     *
     * @param newPasswordHash nuovo hash della password; non può essere {@code null} né vuoto
     * @throws IllegalArgumentException se {@code newPasswordHash} è {@code null} o vuoto
     */
    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be null, empty or blank");
        }
        this.passwordHash = newPasswordHash;
    }

    /**
     * Sostituisce i ruoli correnti dell'utente con quelli forniti.
     *
     * @param newRoles nuovo elenco di ruoli; non può essere {@code null} e nessun ruolo può essere {@code null} o vuoto
     * @throws IllegalArgumentException se {@code newRoles} è {@code null} oppure se contiene un ruolo {@code null} o vuoto
     */
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

    /**
     * Restituisce l'identificativo univoco dell'utente.
     *
     * @return l'identificativo dell'utente, mai {@code null}
     */
    public UserId getId() {
        return id;
    }

    /**
     * Restituisce il nome utente.
     *
     * @return il nome utente, mai {@code null} né vuoto
     */
    public String getUsername() {
        return username;
    }

    /**
     * Restituisce l'hash della password dell'utente.
     *
     * @return l'hash della password, mai {@code null} né vuoto
     * @see #changePassword(String)
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Restituisce l'indirizzo email dell'utente.
     *
     * @return l'indirizzo email, mai {@code null} né vuoto
     */
    public String getEmail() {
        return email;
    }

    /**
     * Restituisce l'elenco immutabile dei ruoli assegnati all'utente.
     *
     * @return la lista non modificabile dei ruoli, mai {@code null}
     * @see #updateRoles(List)
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Restituisce l'istante di creazione dell'utente.
     *
     * @return l'istante di creazione, oppure {@code null} se non specificato
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Confronta questo utente con un altro oggetto verificandone l'uguaglianza
     * sulla base dell'identificativo dell'utente.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code User} con lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo dell'utente.
     *
     * @return il codice hash dell'utente
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

