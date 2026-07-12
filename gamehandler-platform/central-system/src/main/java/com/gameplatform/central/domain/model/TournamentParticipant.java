package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a single registered participant (individual or
 * team) in a tournament, persisted in the Central
 * {@code tournament_participants} table (FASE 4 PIANO &sect;3.3). A
 * polymorphic snapshot: {@code participantId} resolves either to a
 * {@link com.gameplatform.shared.domain.model.UserId} (when
 * {@link #isTeam()} is {@code false}) or to a
 * {@link com.gameplatform.shared.domain.model.TeamId} (when {@code true}).
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code PlayerStatistics} POJO convention. Identity
 * is the composite ({@code tournamentId}, {@code participantId}) pair.
 * Fully immutable.</p>
 */
public class TournamentParticipant {
    private final TournamentId tournamentId;
    private final String participantId;
    private final boolean isTeam;
    private final String displayName;
    private final Instant registeredAt;

    public TournamentParticipant(TournamentId tournamentId, String participantId, boolean isTeam,
                                 String displayName, Instant registeredAt) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (participantId == null || participantId.isBlank()) throw new IllegalArgumentException("participantId cannot be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank");
        if (registeredAt == null) throw new IllegalArgumentException("registeredAt cannot be null");
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public boolean isTeam() {
        return isTeam;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipant that = (TournamentParticipant) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
