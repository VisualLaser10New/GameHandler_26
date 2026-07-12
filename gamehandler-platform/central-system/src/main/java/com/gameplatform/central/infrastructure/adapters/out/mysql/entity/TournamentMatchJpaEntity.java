package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tournament_matches")
public class TournamentMatchJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Column(name = "round", nullable = false)
    private Integer round;
    @Column(name = "bracket_position", nullable = false)
    private Integer bracketPosition;
    @Column(name = "participant_a", length = 36, nullable = false)
    private String participantA;
    @Column(name = "participant_b", length = 36)
    private String participantB;
    @Column(name = "building_id", length = 100)
    private String buildingId;
    @Column(name = "game_id", length = 100)
    private String gameId;
    @Column(name = "session_id", length = 36)
    private String sessionId;
    @Column(name = "winner", length = 36)
    private String winner;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Column(name = "played_at")
    private Instant playedAt;
    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData;

    public TournamentMatchJpaEntity() {
    }

    public TournamentMatchJpaEntity(String id, String tournamentId, Integer round, Integer bracketPosition,
                                    String participantA, String participantB, String buildingId, String gameId,
                                    String sessionId, String winner, String status, Instant scheduledAt,
                                    Instant playedAt, String resultData) {
        this.id = id;
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public Integer getBracketPosition() { return bracketPosition; }
    public void setBracketPosition(Integer bracketPosition) { this.bracketPosition = bracketPosition; }
    public String getParticipantA() { return participantA; }
    public void setParticipantA(String participantA) { this.participantA = participantA; }
    public String getParticipantB() { return participantB; }
    public void setParticipantB(String participantB) { this.participantB = participantB; }
    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
    public String getResultData() { return resultData; }
    public void setResultData(String resultData) { this.resultData = resultData; }
}