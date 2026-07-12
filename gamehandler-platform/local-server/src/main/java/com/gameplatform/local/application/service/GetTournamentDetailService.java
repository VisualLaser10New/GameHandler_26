package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.in.GetTournamentDetailUseCase;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.domain.ports.out.TournamentParticipantsLocalRepository;
import com.gameplatform.local.domain.ports.out.TournamentStandingsLocalRepository;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDetailDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read use case (PIANO §7.B): aggregates the detail view of a single
 * tournament (summary + standings + matches + participants) from the
 * four local replicas. Empty if the tournament summary row is missing
 * or marked as deleted.
 */
@Service
@Transactional(readOnly = true)
public class GetTournamentDetailService implements GetTournamentDetailUseCase {

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final TournamentStandingsLocalRepository tournamentStandingsLocalRepository;
    private final TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository;
    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;

    public GetTournamentDetailService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                       TournamentStandingsLocalRepository tournamentStandingsLocalRepository,
                                       TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository,
                                       TournamentMatchLocalRepository tournamentMatchLocalRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.tournamentStandingsLocalRepository = tournamentStandingsLocalRepository;
        this.tournamentParticipantsLocalRepository = tournamentParticipantsLocalRepository;
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
    }

    @Override
    public Optional<TournamentDetailDto> getDetail(String tournamentId) {
        if (tournamentId == null || tournamentId.isBlank()) {
            return Optional.empty();
        }
        TournamentId id = new TournamentId(tournamentId);
        Optional<TournamentSummaryLocal> summaryOpt = tournamentSummaryLocalRepository.findById(id);
        if (summaryOpt.isEmpty() || summaryOpt.get().isDeleted()) {
            return Optional.empty();
        }
        TournamentSummaryLocal summary = summaryOpt.get();

        List<TournamentStandingDto> standings = tournamentStandingsLocalRepository.findByTournament(id).stream()
                .map(GetTournamentDetailService::toStandingDto)
                .collect(Collectors.toList());
        List<TournamentParticipantViewDto> participants = tournamentParticipantsLocalRepository.findByTournament(id).stream()
                .map(GetTournamentDetailService::toParticipantView)
                .collect(Collectors.toList());
        List<TournamentMatchLocal> matches = tournamentMatchLocalRepository.findByTournamentId(id);
        List<TournamentMatchDto> matchDtos = matches != null ? matches.stream()
                .map(GetTournamentDetailService::toMatchDto)
                .collect(Collectors.toList()) : List.of();
        return Optional.of(new TournamentDetailDto(
                toSummaryDto(summary), standings, matchDtos, participants
        ));
    }

    private static TournamentSummaryDto toSummaryDto(TournamentSummaryLocal summary) {
        return new TournamentSummaryDto(
                summary.getTournamentId().value(),
                summary.getName(),
                summary.getGameType(),
                summary.isTeamBased(),
                summary.getTeamSize(),
                summary.getStatus(),
                summary.getStartsAt(),
                summary.getEndsAt(),
                summary.getBuildingIds(),
                summary.getParticipantsCount(),
                summary.getUpdatedAt()
        );
    }

    private static TournamentStandingDto toStandingDto(TournamentStandingLocal standing) {
        return new TournamentStandingDto(
                standing.getParticipantId(),
                standing.getDisplayName(),
                standing.getWins(),
                standing.getLosses(),
                standing.getPoints(),
                standing.getRank()
        );
    }

    private static TournamentParticipantViewDto toParticipantView(TournamentParticipantLocal participant) {
        return new TournamentParticipantViewDto(
                participant.getParticipantId(),
                participant.isTeam(),
                participant.getDisplayName(),
                participant.getRegisteredAt()
        );
    }

    private static TournamentMatchDto toMatchDto(TournamentMatchLocal match) {
        return new TournamentMatchDto(
                match.getId().value(),
                match.getRound(),
                match.getBracketPosition(),
                match.getParticipantA(),
                match.getParticipantB(),
                null,
                match.getGameId(),
                match.getStatus(),
                match.getScheduledAt(),
                null
        );
    }
}