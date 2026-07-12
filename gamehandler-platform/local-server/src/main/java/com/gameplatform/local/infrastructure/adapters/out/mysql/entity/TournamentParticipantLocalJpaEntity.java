package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_participants_local} (PIANO §7.B).
 * Read-only replica updated only by sync; composite PK
 * ({@code tournamentId}, {@code participantId}) via
 * {@link TournamentParticipantLocalId}; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code TournamentMatchLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_participants_local", indexes = {
        @Index(name = "idx_tpl_tournament", columnList = "tournament_id")
})
@IdClass(TournamentParticipantLocalId.class)
public class TournamentParticipantLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Id
    @Column(name = "participant_id", length = 64, nullable = false)
    private String participantId;

    @Column(name = "is_team", nullable = false)
    private Boolean isTeam;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TournamentParticipantLocalJpaEntity() {
    }

    public TournamentParticipantLocalJpaEntity(String tournamentId, String participantId, Boolean isTeam,
                                               String displayName, Instant registeredAt, Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public Boolean getIsTeam() {
        return isTeam;
    }

    public void setIsTeam(Boolean isTeam) {
        this.isTeam = isTeam;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
