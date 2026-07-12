package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tournament_participants")
@IdClass(TournamentParticipantId.class)
public class TournamentParticipantJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "participant_id", length = 36, nullable = false)
    private String participantId;
    @Column(name = "is_team", nullable = false)
    private Boolean isTeam;
    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public TournamentParticipantJpaEntity() {
    }

    public TournamentParticipantJpaEntity(String tournamentId, String participantId, Boolean isTeam,
                                          String displayName, Instant registeredAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
    }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public Boolean getIsTeam() { return isTeam; }
    public void setIsTeam(Boolean isTeam) { this.isTeam = isTeam; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}