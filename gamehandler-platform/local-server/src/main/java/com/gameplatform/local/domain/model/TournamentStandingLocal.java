package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only local replica of a single tournament standings row
 * (PIANO §7.B), the flattened Central→Local projection of
 * {@code TOURNAMENT_STANDINGS_UPSERTED} events. Pure Java POJO,
 * immutable, identity = ({@code tournamentId}, {@code participantId})
 * composite key — mirror of the Central {@code TournamentStanding} model
 * plus the extra {@code displayName} and {@code updatedAt} envelope
 * fields needed for the local read views.
 */
public class TournamentStandingLocal {

    private final TournamentId tournamentId;
    private final String participantId;
    private final String displayName;
    private final int wins;
    private final int losses;
    private final int points;
    private final Integer rank;
    private final Instant updatedAt;

    public TournamentStandingLocal(TournamentId tournamentId, String participantId, String displayName,
                                   int wins, int losses, int points, Integer rank, Instant updatedAt) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("participantId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins must be >= 0");
        }
        if (losses < 0) {
            throw new IllegalArgumentException("losses must be >= 0");
        }
        if (points < 0) {
            throw new IllegalArgumentException("points must be >= 0");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.displayName = displayName;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
        this.updatedAt = updatedAt;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getPoints() {
        return points;
    }

    public Integer getRank() {
        return rank;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStandingLocal that = (TournamentStandingLocal) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
