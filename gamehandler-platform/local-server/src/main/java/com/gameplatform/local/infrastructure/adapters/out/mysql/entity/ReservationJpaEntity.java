package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * JPA entity per la tabella {@code reservations}.
 * Rappresenta una prenotazione effettuata da un utente per una postazione
 * gioco in una determinata finestra temporale, con stato e optimistic locking.
 *
 * @see GameJpaEntity
 * @see LocalUserJpaEntity
 */
@Entity
@Table(name = "reservations")
public class ReservationJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public ReservationJpaEntity() {
    }

    /**
     * Costruisce una nuova prenotazione con tutti i campi.
     *
     * @param id        identificatore univoco della prenotazione
     * @param gameId    identificatore della postazione gioco
     * @param userId    identificatore dell'utente che prenota
     * @param status    stato della prenotazione
     * @param startTime istante di inizio prenotazione
     * @param endTime   istante di fine prenotazione
     * @param createdAt istante di creazione della prenotazione
     */
    public ReservationJpaEntity(String id, String gameId, String userId, String status, Instant startTime, Instant endTime, Instant createdAt) {
        this.id = id;
        this.gameId = gameId;
        this.userId = userId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificatore univoco della prenotazione.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco della prenotazione.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'identificatore della postazione gioco prenotata.
     *
     * @return gameId
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Imposta l'identificatore della postazione gioco.
     *
     * @param gameId nuovo identificatore postazione
     */
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    /**
     * Restituisce l'identificatore dell'utente che ha effettuato la prenotazione.
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificatore dell'utente.
     *
     * @param userId nuovo identificatore utente
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Restituisce lo stato della prenotazione.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato della prenotazione.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce l'istante di inizio della prenotazione.
     *
     * @return startTime
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Imposta l'istante di inizio della prenotazione.
     *
     * @param startTime nuovo istante di inizio
     */
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    /**
     * Restituisce l'istante di fine della prenotazione.
     *
     * @return endTime
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * Imposta l'istante di fine della prenotazione.
     *
     * @param endTime nuovo istante di fine
     */
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    /**
     * Restituisce l'istante di creazione della prenotazione.
     *
     * @return createdAt
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione della prenotazione.
     *
     * @param createdAt nuovo istante di creazione
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
