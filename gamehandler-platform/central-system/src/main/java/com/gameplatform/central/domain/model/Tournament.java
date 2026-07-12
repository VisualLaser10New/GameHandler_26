package com.gameplatform.central.domain.model;

import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a tournament in the Central Source-of-Truth,
 * embodying the immutable state-machine lifecycle defined in PIANO FASE 4
 * &sect;3.1 ({@code DRAFT} &rarr; {@code OPEN_REGISTRATION} &rarr;
 * {@code IN_PROGRESS} &rarr; {@code COMPLETED}, with {@code CANCELLED} as a
 * terminal side-state reachable from {@code DRAFT} or
 * {@code OPEN_REGISTRATION}).
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code PlayerStatistics} POJO convention. Identity is
 * the {@code tournamentId} (primary key). Transition methods
 * ({@link #openRegistration()}, {@link #cancel()}, {@link #startProgress()},
 * {@link #complete(Instant)}) return a NEW immutable instance with the updated
 * {@link TournamentStatus}; the receiver is never mutated. Illegal source
 * states raise {@link InvalidTournamentStateException}.</p>
 *
 * <p>{@link #startProgress()} and {@link #complete(Instant)} are
 * <em>forward-declared</em> for FASE 5 (match execution) and FASE 6
 * (tournament completion) respectively; they are not invoked by any FASE 4
 * code path but are placed here to keep the state machine cohesive and to
 * localise the transition invariants in one type.</p>
 */
public class Tournament {
    private final TournamentId tournamentId;
    private final String name;
    private final GameType gameType;
    private final boolean teamBased;
    private final int teamSize;
    private final TournamentFormat format;
    private final TournamentStatus status;
    private final Instant startsAt;
    private final Instant endsAt;
    private final UserId createdBy;
    private final Instant createdAt;

    public Tournament(TournamentId tournamentId, String name, GameType gameType, boolean teamBased,
                      int teamSize, TournamentFormat format, TournamentStatus status, Instant startsAt,
                      Instant endsAt, UserId createdBy, Instant createdAt) {
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (gameType == null) throw new IllegalArgumentException("gameType cannot be null");
        if (format == null) throw new IllegalArgumentException("format cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        if (teamSize < 1) throw new IllegalArgumentException("teamSize must be >= 1");
        if (!teamBased && teamSize != 1) throw new IllegalArgumentException("Individual tournament must have teamSize == 1");
        if (startsAt == null) throw new IllegalArgumentException("startsAt cannot be null");
        if (createdBy == null) throw new IllegalArgumentException("createdBy cannot be null");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.format = format;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Returns a new {@code Tournament} with status
     * {@link TournamentStatus#OPEN_REGISTRATION}. Legal only when the current
     * status is {@link TournamentStatus#DRAFT}; the receiver is left unchanged
     * (immutable transition).
     *
     * @return a new immutable {@code Tournament} in
     *         {@code OPEN_REGISTRATION} state
     * @throws InvalidTournamentStateException if {@code status != DRAFT}
     */
    public Tournament openRegistration() {
        if (status != TournamentStatus.DRAFT) {
            throw new InvalidTournamentStateException("Cannot open registration from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.OPEN_REGISTRATION, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Returns a new {@code Tournament} with status
     * {@link TournamentStatus#CANCELLED}. Legal only when the current status
     * is {@link TournamentStatus#DRAFT} or
     * {@link TournamentStatus#OPEN_REGISTRATION}; the receiver is left
     * unchanged (immutable transition).
     *
     * @return a new immutable {@code Tournament} in {@code CANCELLED} state
     * @throws InvalidTournamentStateException if {@code status} is neither
     *         {@code DRAFT} nor {@code OPEN_REGISTRATION}
     */
    public Tournament cancel() {
        if (status != TournamentStatus.DRAFT && status != TournamentStatus.OPEN_REGISTRATION) {
            throw new InvalidTournamentStateException("Cannot cancel from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.CANCELLED, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Returns a new {@code Tournament} with status
     * {@link TournamentStatus#IN_PROGRESS}. Legal only when the current
     * status is {@link TournamentStatus#OPEN_REGISTRATION}; the receiver is
     * left unchanged (immutable transition).
     *
     * <p><em>Forward-declared for FASE 5</em>: not invoked by any FASE 4 code
     * path; bracket generation in {@code TournamentMatchSetupService} (FASE 5)
     * will be its first caller.</p>
     *
     * @return a new immutable {@code Tournament} in {@code IN_PROGRESS} state
     * @throws InvalidTournamentStateException if {@code status != OPEN_REGISTRATION}
     */
    public Tournament startProgress() {
        if (status != TournamentStatus.OPEN_REGISTRATION) {
            throw new InvalidTournamentStateException("Cannot start progress from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.IN_PROGRESS, startsAt, endsAt, createdBy, createdAt);
    }

    /**
     * Returns a new {@code Tournament} with status
     * {@link TournamentStatus#COMPLETED} and {@code endsAt} set to
     * {@code endedAt}. Legal only when the current status is
     * {@link TournamentStatus#IN_PROGRESS} and {@code endedAt} is non-null;
     * the receiver is left unchanged (immutable transition).
     *
     * <p><em>Forward-declared for FASE 6</em>: not invoked by any FASE 4 code
     * path; tournament finalisation in {@code TournamentCompletionService}
     * (FASE 6) will be its first caller.</p>
     *
     * @param endedAt the instant at which the tournament actually ended
     *                (must not be {@code null})
     * @return a new immutable {@code Tournament} in {@code COMPLETED} state
     *         with {@code endsAt = endedAt}
     * @throws InvalidTournamentStateException if {@code status != IN_PROGRESS}
     *         or {@code endedAt == null}
     */
    public Tournament complete(Instant endedAt) {
        if (status != TournamentStatus.IN_PROGRESS || endedAt == null) {
            throw new InvalidTournamentStateException("Cannot complete from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                TournamentStatus.COMPLETED, startsAt, endedAt, createdBy, createdAt);
    }

    /**
     * Returns a new {@code Tournament} with the mutated mutable fields
     * ({@code name}, {@code startsAt}) and {@code endsAt = null}. Legal only
     * when the current status is {@link TournamentStatus#DRAFT}; the receiver
     * is left unchanged (immutable transition). Used by use case §7.A.1
     * (UpdateTournamentService).
     *
     * @param name     the new tournament name
     * @param startsAt the new scheduled start instant
     * @return a new immutable {@code Tournament} in {@code DRAFT} state with
     *         updated {@code name}/{@code startsAt} and {@code endsAt = null}
     * @throws InvalidTournamentStateException if {@code status != DRAFT}
     */
    public Tournament update(String name, Instant startsAt) {
        if (status != TournamentStatus.DRAFT) {
            throw new InvalidTournamentStateException("Cannot update from status " + status);
        }
        return new Tournament(tournamentId, name, gameType, teamBased, teamSize, format,
                status, startsAt, null, createdBy, createdAt);
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

    public TournamentFormat getFormat() {
        return format;
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

    public UserId getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tournament that = (Tournament) o;
        return Objects.equals(tournamentId, that.tournamentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId);
    }
}
