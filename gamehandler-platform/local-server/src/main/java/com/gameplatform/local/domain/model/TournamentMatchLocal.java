package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only replica of a tournament match destined for THIS building
 * (PIANO §3.4). NO {@code buildingId} (the table only holds matches routed
 * to this building — ambiguity O), NO {@code winner}, NO {@code playedAt},
 * NO {@code resultData} (those are central-only). Pure Java POJO, immutable,
 * identity = {@code id}. {@code status} is mutable via a new-instance
 * {@code withStatus(...)} helper so the sync service can flip SCHEDULED →
 * IN_PROGRESS → COMPLETED/ABANDONED idempotently.
 */
public class TournamentMatchLocal {

    private final TournamentMatchId id;
    private final TournamentId tournamentId;
    private final int round;
    private final int bracketPosition;
    private final String participantA;
    private final String participantB;   // nullable (BYE never replicated, but kept nullable)
    private final GameType gameType;
    private final String gameId;          // nullable
    private final TournamentMatchStatus status;
    private final Instant scheduledAt;    // nullable

    public TournamentMatchLocal(TournamentMatchId id, TournamentId tournamentId, int round, int bracketPosition,
                                String participantA, String participantB, GameType gameType,
                                String gameId, TournamentMatchStatus status, Instant scheduledAt) {
        if (id == null) {
            throw new IllegalArgumentException("TournamentMatchId cannot be null");
        }
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (participantA == null || participantA.isBlank()) {
            throw new IllegalArgumentException("participantA cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("TournamentMatchStatus cannot be null");
        }
        this.id = id;
        this.tournamentId = tournamentId;
        this.round = round;
        this.bracketPosition = bracketPosition;
        this.participantA = participantA;
        this.participantB = participantB;
        this.gameType = gameType;
        this.gameId = gameId;
        this.status = status;
        this.scheduledAt = scheduledAt;
    }

    public TournamentMatchId getId() {
        return id;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public int getRound() {
        return round;
    }

    public int getBracketPosition() {
        return bracketPosition;
    }

    public String getParticipantA() {
        return participantA;
    }

    public String getParticipantB() {
        return participantB;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getGameId() {
        return gameId;
    }

    public TournamentMatchStatus getStatus() {
        return status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /** New immutable copy with updated status (used by start/end/abort flows). */
    public TournamentMatchLocal withStatus(TournamentMatchStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("TournamentMatchStatus cannot be null");
        }
        return new TournamentMatchLocal(
                this.id,
                this.tournamentId,
                this.round,
                this.bracketPosition,
                this.participantA,
                this.participantB,
                this.gameType,
                this.gameId,
                newStatus,
                this.scheduledAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentMatchLocal that = (TournamentMatchLocal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}