package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_matches_local} (PIANO §3.4 lines 416-427).
 * Read-only replica updated only by sync; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code GameDefinitionLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_matches_local")
public class TournamentMatchLocalJpaEntity {

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

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "game_id", length = 100)
    private String gameId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    public TournamentMatchLocalJpaEntity() {
    }

    public TournamentMatchLocalJpaEntity(String id, String tournamentId, Integer round, Integer bracketPosition,
                                         String participantA, String participantB, String gameType,
                                         String gameId, String status, Instant scheduledAt) {
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public Integer getBracketPosition() {
        return bracketPosition;
    }

    public void setBracketPosition(Integer bracketPosition) {
        this.bracketPosition = bracketPosition;
    }

    public String getParticipantA() {
        return participantA;
    }

    public void setParticipantA(String participantA) {
        this.participantA = participantA;
    }

    public String getParticipantB() {
        return participantB;
    }

    public void setParticipantB(String participantB) {
        this.participantB = participantB;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}