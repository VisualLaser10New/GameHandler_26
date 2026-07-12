package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.in.ListTournamentSummariesUseCase;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read use case (PIANO §7.B): returns the {@code tournaments_summary_local}
 * rows (optionally filtered by status), projected to
 * {@link TournamentSummaryDto}. Only non-deleted rows are returned — the
 * {@code TournamentSummarySyncService} physically removes tombstones so
 * every persisted row is non-deleted in steady state.
 */
@Service
@Transactional(readOnly = true)
public class ListTournamentSummariesService implements ListTournamentSummariesUseCase {

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;

    public ListTournamentSummariesService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
    }

    @Override
    public List<TournamentSummaryDto> listSummaries(TournamentStatus statusFilter) {
        return tournamentSummaryLocalRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .filter(s -> statusFilter == null || s.getStatus() == statusFilter)
                .map(ListTournamentSummariesService::toDto)
                .collect(Collectors.toList());
    }

    private static TournamentSummaryDto toDto(TournamentSummaryLocal summary) {
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
}