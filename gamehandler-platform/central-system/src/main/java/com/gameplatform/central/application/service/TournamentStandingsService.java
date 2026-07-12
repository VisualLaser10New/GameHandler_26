package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements {@link GetTournamentStandingsUseCase} for the
 * {@code /api/tournaments/{id}/standings} endpoint.
 *
 * <p>Class-level {@code @Transactional} because {@link #seedStandings} writes;
 * {@link #getStandings} is overridden at the method level with
 * {@code @Transactional(readOnly = true)}.</p>
 *
 * <p>{@link #seedStandings} is package-visible (NOT on the in-port interface) so
 * the orchestrating {@code TournamentBracketService.schedule(...)} in the same
 * package can call it inside the same Spring transaction; this keeps the seed
 * entry-point hidden from the controller while preserving hexagonal hygiene.</p>
 *
 * <p>FASE 7-A3: {@link #recomputeAfterCompletion} now emits a
 * {@code TOURNAMENT_STANDINGS_UPSERTED} outbox event carrying a full snapshot of
 * the per-tournament standings, so every active Local Server can mirror the
 * standings projection (delete+insert by {@code tournamentId}). The
 * {@code originatingRequestId} is {@code null} on the FASE 5/6 path (match
 * completion); the outbox save is atomic with the standings update inside the
 * caller's {@code @Transactional}.</p>
 */
@Service
@Transactional
public class TournamentStandingsService implements GetTournamentStandingsUseCase {

    private static final String STANDINGS_EVENT_TYPE = "TOURNAMENT_STANDINGS_UPSERTED";

    private final TournamentStandingRepository tournamentStandingRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final Clock clock;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public TournamentStandingsService(TournamentStandingRepository tournamentStandingRepository,
                                      TournamentParticipantRepository tournamentParticipantRepository,
                                      TournamentMatchRepository tournamentMatchRepository,
                                      Clock clock,
                                      OutboxEventRepository outboxEventRepository,
                                      ObjectMapper objectMapper) {
        this.tournamentStandingRepository = tournamentStandingRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.clock = clock;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compat legacy ctor (pattern {@code SyncEventProcessor:91-146}):
     * 4-arg delegating to the 6-arg production ctor with {@code null} for the
     * FASE 7-A3 outbox deps. When {@code null}, the
     * {@code TOURNAMENT_STANDINGS_UPSERTED} emit is skipped (no-op), preserving
     * the historical FASE 5 behaviour for existing unit tests.
     */
    public TournamentStandingsService(TournamentStandingRepository tournamentStandingRepository,
                                      TournamentParticipantRepository tournamentParticipantRepository,
                                      TournamentMatchRepository tournamentMatchRepository,
                                      Clock clock) {
        this(tournamentStandingRepository, tournamentParticipantRepository, tournamentMatchRepository,
                clock, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentStandingDto> getStandings(TournamentId tournamentId) {
        return buildStandingsSnapshot(tournamentId);
    }

    /**
     * Builds the sorted {@link TournamentStandingDto} snapshot for a tournament:
     * loads standings + participants, resolves display names, sorts by
     * {@code points desc, wins desc, participantId asc}. Shared by
     * {@link #getStandings(TournamentId)} (read path) and the
     * {@code TOURNAMENT_STANDINGS_UPSERTED} outbox emit (replication path).
     */
    private List<TournamentStandingDto> buildStandingsSnapshot(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }

        List<TournamentStanding> standings =
                Optional.ofNullable(tournamentStandingRepository.findByTournament(tournamentId))
                        .orElse(List.of());

        List<TournamentParticipant> participants =
                Optional.ofNullable(tournamentParticipantRepository.findByTournament(tournamentId))
                        .orElse(List.of());

        Map<String, String> displayNameByParticipantId = participants.stream()
                .collect(Collectors.toMap(
                        TournamentParticipant::getParticipantId,
                        TournamentParticipant::getDisplayName,
                        (a, b) -> a));

        return standings.stream()
                .map(standing -> new TournamentStandingDto(
                        standing.getParticipantId(),
                        displayNameByParticipantId.getOrDefault(
                                standing.getParticipantId(), standing.getParticipantId()),
                        standing.getWins(),
                        standing.getLosses(),
                        standing.getPoints(),
                        standing.getRank()))
                .sorted(Comparator.comparingInt(TournamentStandingDto::points).reversed()
                        .thenComparing(Comparator.comparingInt(TournamentStandingDto::wins).reversed())
                        .thenComparing(TournamentStandingDto::participantId))
                .collect(Collectors.toList());
    }

    /**
     * Idempotent zero-init: writes one
     * {@code TournamentStanding(tournamentId, participantId, 0, 0, 0, null)} per
     * participant if absent.
     *
     * <p>Covers ALL participants — including BYE auto-advancers (BYE rows are
     * persisted by BracketService but their {@code participantA} ALSO appears as a
     * participant; the standings row is purely a counter row and is independent of
     * whether the participant advanced via BYE).</p>
     *
     * <p>Must run inside the caller's {@code @Transactional}
     * (TournamentBracketService.schedule) for atomicity.</p>
     *
     * @param tournamentId   the tournament id (no-op if null)
     * @param participantIds the participant ids to seed (no-op if null)
     */
    void seedStandings(TournamentId tournamentId, List<String> participantIds) {
        if (tournamentId == null || participantIds == null) {
            return;
        }
        for (String participantId : participantIds) {
            if (participantId == null || participantId.isBlank()) {
                continue;
            }
            if (tournamentStandingRepository
                    .findByTournamentAndParticipantId(tournamentId, participantId)
                    .isPresent()) {
                continue;
            }
            tournamentStandingRepository.save(
                    new TournamentStanding(tournamentId, participantId, 0, 0, 0, null));
        }
    }

    /**
     * Incrementally recomputes standings after a COMPLETED match: load the
     * match, identify winner &amp; loser; for the winner
     * {@code findByTournamentAndParticipantId} → rebuild with wins+1, points+3
     * → save; for the loser (when non-null) rebuild with losses+1 → save.
     *
     * <p>NO-OP when the match is absent or has no winner (an ABANDONED match
     * whose walkover winner could not be resolved). The caller
     * ({@code SyncEventProcessor.handleTournamentMatchCompleted}) already
     * guards the ABANDONED path, but this method is defensive.</p>
     *
     * <p>FASE 7-A3: after the winner/loser updates, emits a
     * {@code TOURNAMENT_STANDINGS_UPSERTED} outbox event carrying a full
     * snapshot of the per-tournament standings so every active Local Server can
     * mirror the projection (delete+insert by {@code tournamentId}). The
     * outbox save is atomic with the standings update inside the caller's
     * {@code @Transactional}. The emit is skipped when the outbox deps are
     * {@code null} (legacy test ctor).</p>
     *
     * @param matchId the completed match id (no-op if null or absent)
     */
    public void recomputeAfterCompletion(TournamentMatchId matchId) {
        if (matchId == null) {
            return;
        }
        Optional<TournamentMatch> matchOpt = tournamentMatchRepository.findById(matchId);
        if (matchOpt.isEmpty()) {
            return;
        }
        TournamentMatch match = matchOpt.get();
        String winner = match.getWinner();
        if (winner == null) {
            return; // ABANDONED with no walkover winner — skip.
        }
        String loser = winner.equals(match.getParticipantA())
                ? match.getParticipantB() : match.getParticipantA();

        // Winner: wins+1, points+3.
        Optional<TournamentStanding> winnerOpt =
                tournamentStandingRepository.findByTournamentAndParticipantId(match.getTournamentId(), winner);
        if (winnerOpt.isPresent()) {
            TournamentStanding ws = winnerOpt.get();
            tournamentStandingRepository.save(new TournamentStanding(
                    ws.getTournamentId(), ws.getParticipantId(),
                    ws.getWins() + 1, ws.getLosses(), ws.getPoints() + 3, ws.getRank()));
        }

        // Loser (if non-null): losses+1.
        if (loser != null) {
            Optional<TournamentStanding> loserOpt =
                    tournamentStandingRepository.findByTournamentAndParticipantId(match.getTournamentId(), loser);
            if (loserOpt.isPresent()) {
                TournamentStanding ls = loserOpt.get();
                tournamentStandingRepository.save(new TournamentStanding(
                        ls.getTournamentId(), ls.getParticipantId(),
                        ls.getWins(), ls.getLosses() + 1, ls.getPoints(), ls.getRank()));
            }
        }

        writeStandingsOutbox(match.getTournamentId(), null);
    }

    /**
     * Serialises a {@link TournamentStandingsEventDto} carrying the full
     * standings snapshot and writes it to the outbox. Mirrors
     * {@code TournamentService.writeOutboxEvent}: a single UUID is shared by the
     * outbox event id and the DTO {@code eventId}. No-op when the outbox deps
     * are {@code null} (legacy test ctor).
     */
    private void writeStandingsOutbox(TournamentId tournamentId, String originatingRequestId) {
        if (outboxEventRepository == null || objectMapper == null) {
            return;
        }
        String eventId = UUID.randomUUID().toString();
        List<TournamentStandingDto> entries = buildStandingsSnapshot(tournamentId);
        TournamentStandingsEventDto dto = new TournamentStandingsEventDto(
                eventId,
                STANDINGS_EVENT_TYPE,
                tournamentId.value(),
                entries,
                originatingRequestId,
                Instant.now(clock));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TournamentStandingsEventDto", e);
        }
        OutboxEvent event = new OutboxEvent(
                eventId, STANDINGS_EVENT_TYPE, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }

    /**
     * Assigns final ranks: load all standings via
     * {@code findByTournament(tournamentId)}, sort by
     * {@code points desc, wins desc, participantId asc}, assign
     * {@code rank = 1, 2, 3, ...} by rebuilding each {@link TournamentStanding}
     * with the new rank → save.
     *
     * @param tournamentId the tournament id (no-op if null)
     */
    public void assignFinalRanks(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        List<TournamentStanding> standings =
                Optional.ofNullable(tournamentStandingRepository.findByTournament(tournamentId))
                        .orElse(List.of());
        if (standings.isEmpty()) {
            return;
        }
        List<TournamentStanding> sorted = new ArrayList<>(standings);
        sorted.sort(Comparator.comparingInt(TournamentStanding::getPoints).reversed()
                .thenComparing(Comparator.comparingInt(TournamentStanding::getWins).reversed())
                .thenComparing(TournamentStanding::getParticipantId));
        int rank = 1;
        for (TournamentStanding s : sorted) {
            tournamentStandingRepository.save(new TournamentStanding(
                    s.getTournamentId(), s.getParticipantId(),
                    s.getWins(), s.getLosses(), s.getPoints(), rank++));
        }
    }
}
