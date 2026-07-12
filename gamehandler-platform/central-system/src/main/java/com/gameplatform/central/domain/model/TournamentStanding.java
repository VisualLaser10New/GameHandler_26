package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.util.Objects;

/**
 * Domain read-model representing a single participant's cumulative standings
 * row within a tournament, owned by the Central Source-of-Truth
 * ({@code tournament_standings} table, FASE 4 PIANO &sect;3.5). Recomputed
 * incrementally by FASE 5/6 event projections on each
 * {@code TOURNAMENT_MATCH_COMPLETED} event.
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code PlayerStatistics} POJO convention. Identity
 * is the composite ({@code tournamentId}, {@code participantId}) pair.
 * {@code rank} is nullable (boxed {@link Integer}) until the closing
 * recalculation assigns final rankings. Fully immutable in FASE 4; mutation
 * methods for incremental re-scoring will be added in FASE 5/6.</p>
 */
public class TournamentStanding {
    private final TournamentId tournamentId;
    private final String participantId;
    private final int wins;
    private final int losses;
    private final int points;
    private final Integer rank;

    public TournamentStanding(TournamentId tournamentId, String participantId, int wins, int losses,
                              int points, Integer rank) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (participantId == null || participantId.isBlank()) throw new IllegalArgumentException("participantId cannot be blank");
        if (wins < 0) throw new IllegalArgumentException("wins must be >= 0");
        if (losses < 0) throw new IllegalArgumentException("losses must be >= 0");
        if (points < 0) throw new IllegalArgumentException("points must be >= 0");
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getParticipantId() {
        return participantId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStanding that = (TournamentStanding) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
