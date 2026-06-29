package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class Reservation {
    private final ReservationId id;
    private final GameId gameId;
    private final UserId userId;
    private ReservationStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant createdAt;

    public Reservation(ReservationId id, GameId gameId, UserId userId, ReservationStatus status,
                       Instant startTime, Instant endTime, Instant createdAt) {
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
    }

    public boolean canBeCancelled(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }
        return status == ReservationStatus.PENDING &&
               startTime.isAfter(Instant.now(clock).plus(Duration.ofHours(1)));
    }

    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidGameStateTransitionException("Cannot confirm reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidGameStateTransitionException("Cannot cancel reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if (this.status != ReservationStatus.PENDING && this.status != ReservationStatus.CONFIRMED) {
            throw new InvalidGameStateTransitionException("Cannot expire reservation because status is: " + this.status);
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public ReservationId getId() {
        return id;
    }

    public GameId getGameId() {
        return gameId;
    }

    public UserId getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

