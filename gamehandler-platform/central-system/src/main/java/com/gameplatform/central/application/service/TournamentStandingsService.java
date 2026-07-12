package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final Clock clock;

    public TournamentStandingsService(TournamentStandingRepository tournamentStandingRepository,
                                      TournamentParticipantRepository tournamentParticipantRepository,
                                      Clock clock) {
        this.tournamentStandingRepository = tournamentStandingRepository;
        this.tournamentParticipantRepository = tournamentParticipantRepository;
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
}
