package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>FASE 6 will add {@code recomputeAfterCompletion(matchId)} and final-rank
 * assignment to this service; FASE 5 deliberately only seeds + reads.</p>
 */
@Service
@Transactional
public class TournamentStandingsService implements GetTournamentStandingsUseCase {

    private final TournamentStandingRepository tournamentStandingRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final Clock clock;

    public TournamentStandingsService(TournamentStandingRepository tournamentStandingRepository,
                                      TournamentParticipantRepository tournamentParticipantRepository,
                                      TournamentMatchRepository tournamentMatchRepository,
                                      Clock clock) {
        this.tournamentStandingRepository = tournamentStandingRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentStandingDto> getStandings(TournamentId tournamentId) {
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
