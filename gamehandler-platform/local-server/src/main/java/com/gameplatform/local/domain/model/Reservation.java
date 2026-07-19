package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Modello del dominio che rappresenta una prenotazione per l'utilizzo
 * di una postazione di gioco in una fascia oraria specifica. Gestisce
 * il ciclo di vita della prenotazione attraverso gli stati PENDING,
 * CONFIRMED, CANCELLED ed EXPIRED, con validazione delle transizioni.
 *
 * @see ReservationStatus
 * @see Game
 */
public class Reservation {
    private final ReservationId id;
    private final GameId gameId;
    private final UserId userId;
    private ReservationStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant createdAt;
    private long version;

    /**
     * Costruisce una nuova prenotazione con versione iniziale 0.
     *
     * @param id        identificatore della prenotazione (non null)
     * @param gameId    identificatore della postazione di gioco (non null)
     * @param userId    identificatore dell'utente prenotante (non null)
     * @param status    stato iniziale (non null)
     * @param startTime istante di inizio fascia oraria (non null)
     * @param endTime   istante di fine fascia oraria (non null, deve essere dopo startTime)
     * @param createdAt istante di creazione (non null)
     * @throws IllegalArgumentException se uno qualsiasi dei parametri è null o endTime precede startTime
     */
    public Reservation(ReservationId id, GameId gameId, UserId userId, ReservationStatus status,
                       Instant startTime, Instant endTime, Instant createdAt) {
        this(id, gameId, userId, status, startTime, endTime, createdAt, 0L);
    }

    /**
     * Costruisce una nuova prenotazione con versione specificata.
     *
     * @param id        identificatore della prenotazione (non null)
     * @param gameId    identificatore della postazione di gioco (non null)
     * @param userId    identificatore dell'utente prenotante (non null)
     * @param status    stato iniziale (non null)
     * @param startTime istante di inizio fascia oraria (non null)
     * @param endTime   istante di fine fascia oraria (non null, deve essere dopo startTime)
     * @param createdAt istante di creazione (non null)
     * @param version   versione per controllo concorrenza ottimistico
     * @throws IllegalArgumentException se uno qualsiasi dei parametri è null o endTime precede startTime
     */
    public Reservation(ReservationId id, GameId gameId, UserId userId, ReservationStatus status,
                       Instant startTime, Instant endTime, Instant createdAt, long version) {
        if (id == null) {
            throw new IllegalArgumentException("ReservationId cannot be null");
        }
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("ReservationStatus cannot be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("StartTime cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("EndTime cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("EndTime cannot be before StartTime");
        }
        this.id = id;
        this.gameId = gameId;
        this.userId = userId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
        this.version = version;
    }

    /**
     * Verifica se la prenotazione può essere cancellata, in base allo stato
     * e alla distanza dall'ora di inizio (almeno 1 ora).
     *
     * @param clock orologio per la determinazione dell'istante corrente
     * @return true se la prenotazione è PENDING e manca almeno 1 ora all'inizio
     * @throws IllegalArgumentException se clock è null
     */
    public boolean canBeCancelled(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }
        return status == ReservationStatus.PENDING &&
               startTime.isAfter(Instant.now(clock).plus(Duration.ofHours(1)));
    }

    /**
     * Conferma la prenotazione, portandola allo stato CONFIRMED.
     *
     * @throws InvalidGameStateTransitionException se la prenotazione non è PENDING
     */
    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidGameStateTransitionException("Cannot confirm reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    /**
     * Cancella la prenotazione, portandola allo stato CANCELLED.
     *
     * @throws InvalidGameStateTransitionException se la prenotazione non è PENDING
     */
    public void cancel() {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidGameStateTransitionException("Cannot cancel reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * Fa scadere la prenotazione, portandola allo stato EXPIRED.
     * Consentito solo se la prenotazione è PENDING o CONFIRMED.
     *
     * @throws InvalidGameStateTransitionException se la prenotazione non è PENDING o CONFIRMED
     */
    public void expire() {
        if (this.status != ReservationStatus.PENDING && this.status != ReservationStatus.CONFIRMED) {
            throw new InvalidGameStateTransitionException("Cannot expire reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.EXPIRED;
    }

    /**
     * Restituisce l'identificatore della prenotazione.
     *
     * @return id
     */
    public ReservationId getId() {
        return id;
    }

    /**
     * Restituisce l'identificatore della postazione di gioco prenotata.
     *
     * @return gameId
     */
    public GameId getGameId() {
        return gameId;
    }

    /**
     * Restituisce l'identificatore dell'utente prenotante.
     *
     * @return userId
     */
    public UserId getUserId() {
        return userId;
    }

    /**
     * Restituisce lo stato corrente della prenotazione.
     *
     * @return status
     */
    public ReservationStatus getStatus() {
        return status;
    }

    /**
     * Restituisce l'istante di inizio della fascia oraria.
     *
     * @return startTime
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Restituisce l'istante di fine della fascia oraria.
     *
     * @return endTime
     */
    public Instant getEndTime() {
        return endTime;
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
     * Restituisce la versione per controllo concorrenza ottimistico.
     *
     * @return version
     */
    public long getVersion() {
        return version;
    }
}

