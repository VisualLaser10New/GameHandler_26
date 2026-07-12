package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a single match within a tournament bracket, owned
 * by the Central Source-of-Truth ({@code tournament_matches} table, FASE 4
 * PIANO &sect;3.4). Pure scaffolding in FASE 4: match lifecycle transitions
 * (schedule / in-progress / complete / abandon / bye) and bracket advancement
 * belong to FASE 5.
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code PlayerStatistics} POJO convention. Identity
 * is the {@code matchId} (primary key). The nullable fields
 * ({@code participantB}, {@code buildingId}, {@code gameId},
 * {@code sessionId}, {@code winner}, {@code scheduledAt}, {@code playedAt},
 * {@code resultData}) are populated progressively across FASE 5/6. Fully
 * immutable in FASE 4; mutation methods will be added in FASE 5.</p>
 */
public class TournamentMatch {
    private final TournamentMatchId matchId;
    private final TournamentId tournamentId;
    private final int round;
    private final int bracketPosition;
    private final String participantA;
    private final String participantB;
    private final String buildingId;
    private final String gameId;
    private final String sessionId;
    private final String winner;
    private final TournamentMatchStatus status;
    private final Instant scheduledAt;
    private final Instant playedAt;
    private final String resultData;

    public TournamentMatch(TournamentMatchId matchId, TournamentId tournamentId, int round, int bracketPosition,
                           String participantA, String participantB, String buildingId, String gameId,
                           String sessionId, String winner, TournamentMatchStatus status,
                           Instant scheduledAt, Instant playedAt, String resultData) {
        if (matchId == null) throw new IllegalArgumentException("matchId cannot be null");
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (round < 1) throw new IllegalArgumentException("round must be >= 1");
        if (bracketPosition < 1) throw new IllegalArgumentException("bracketPosition must be >= 1");
        if (participantA == null || participantA.isBlank()) throw new IllegalArgumentException("participantA cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        this.matchId = matchId;
        this.tournamentId = tournamentId;
        this.round = round;
        this.bracketPosition = bracketPosition;
        this.participantA = participantA;
        this.participantB = participantB;
        this.buildingId = buildingId;
        this.gameId = gameId;
        this.sessionId = sessionId;
        this.winner = winner;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.playedAt = playedAt;
        this.resultData = resultData;
    }

    public TournamentMatchId getMatchId() {
        return matchId;
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

    public String getBuildingId() {
        return buildingId;
    }

    public String getGameId() {
        return gameId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWinner() {
        return winner;
    }

    public TournamentMatchStatus getStatus() {
        return status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public String getResultData() {
        return resultData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentMatch that = (TournamentMatch) o;
        return Objects.equals(matchId, that.matchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId);
    }
}
