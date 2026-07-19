package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code failed_login_attempts} del database MySQL.
 *
 * <p>Registra ogni tentativo di accesso non riuscito associandolo allo username
 * e alla data/ora dell'evento. La tabella è indicizzata sulla coppia
 * {@code (username, attempt_time)} per supportare in modo efficiente le
 * verifiche di soglia di tentativi falliti. Non dichiara relazioni JPA: lo
 * username è mantenuto come colonna testuale secondo la convenzione esagonale
 * adottata nel progetto.</p>
 *
 * @see UserJpaEntity
 */
@Entity
@Table(name = "failed_login_attempts", indexes = {
    @Index(name = "idx_failed_login_username_time", columnList = "username, attempt_time")
})
public class FailedLoginAttemptJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "attempt_time", nullable = false)
    private Instant attemptTime;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public FailedLoginAttemptJpaEntity() {
    }

    /**
     * Costruisce un tentativo di accesso non riuscito con i dati forniti.
     *
     * @param id identificativo univoco del tentativo; non deve essere {@code null}
     * @param username nome utente associato al tentativo fallito; non deve essere {@code null}
     * @param attemptTime data e ora del tentativo; non deve essere {@code null}
     */
    public FailedLoginAttemptJpaEntity(String id, String username, Instant attemptTime) {
        this.id = id;
        this.username = username;
        this.attemptTime = attemptTime;
    }

    /**
     * Restituisce l'identificativo univoco del tentativo di accesso fallito.
     *
     * @return l'identificativo del tentativo; può essere {@code null} se l'entità
     *         non è ancora stata persistita
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificativo univoco del tentativo di accesso fallito.
     *
     * @param id nuovo identificativo del tentativo; può essere {@code null}
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce il nome utente associato al tentativo di accesso fallito.
     *
     * @return il nome utente; non deve essere {@code null}
     */
    public String getUsername() {
        return username;
    }

    /**
     * Imposta il nome utente associato al tentativo di accesso fallito.
     *
     * @param username nuovo nome utente; non deve essere {@code null}
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Restituisce la data e l'ora in cui si è verificato il tentativo fallito.
     *
     * @return l'istante del tentativo; non deve essere {@code null}
     */
    public Instant getAttemptTime() {
        return attemptTime;
    }

    /**
     * Imposta la data e l'ora in cui si è verificato il tentativo fallito.
     *
     * @param attemptTime nuovo istante del tentativo; non deve essere {@code null}
     */
    public void setAttemptTime(Instant attemptTime) {
        this.attemptTime = attemptTime;
    }
}
