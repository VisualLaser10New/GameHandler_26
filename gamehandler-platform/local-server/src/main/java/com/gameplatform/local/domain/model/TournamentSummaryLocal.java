package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read-only local replica of a tournament summary (PIANO §7.B), the flattened
 * Central→Local projection of {@code TOURNAMENT_SUMMARY_UPSERTED} events.
 *
 * <p>NO {@code eventId} / {@code eventType} / {@code originatingRequestId}
 * (those are outbox-envelope fields that the sync service consumes but does not
 * persist on the projection). Pure Java POJO, immutable, identity =
 * {@code tournamentId} — mirror of {@link TournamentMatchLocal} and
 * {@link GameDefinitionLocal}. The {@code deleted} flag is the column that a
 * tombstone {@code deleted=true} upstream event cleans up via
 * {@code deleteById}; on the projection it is stored for read-side filtering
 * (the sync service PHYSICALLY deletes the row on a tombstone, so a
 * non-deleted projection row always has {@code deleted=false}).</p>
 */
public class TournamentSummaryLocal {

    private final TournamentId tournamentId;
    private final String name;
    private final GameType gameType;
    private final boolean teamBased;
    private final int teamSize;
    private final TournamentStatus status;
    private final Instant startsAt;
    private final Instant endsAt;
    private final List<String> buildingIds;
    private final int participantsCount;
    private final boolean deleted;
    private final Instant updatedAt;

    public TournamentSummaryLocal(TournamentId tournamentId, String name, GameType gameType, boolean teamBased,
                                  int teamSize, TournamentStatus status, Instant startsAt, Instant endsAt,
                                  List<String> buildingIds, int participantsCount, boolean deleted, Instant updatedAt) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("TournamentStatus cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.buildingIds = buildingIds != null ? List.copyOf(buildingIds) : List.of();
        this.participantsCount = participantsCount;
        this.deleted = deleted;
        this.updatedAt = updatedAt;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public GameType getGameType() {
        return gameType;
    }

    public boolean isTeamBased() {
        return teamBased;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public List<String> getBuildingIds() {
        return buildingIds;
    }

    public int getParticipantsCount() {
        return participantsCount;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentSummaryLocal that = (TournamentSummaryLocal) o;
        return Objects.equals(tournamentId, that.tournamentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId);
    }
}
