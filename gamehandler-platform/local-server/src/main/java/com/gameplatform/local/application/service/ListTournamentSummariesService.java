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
 * Caso d'uso in lettura (PIANO §7.B): restituisce le righe
 * {@code tournaments_summary_local} (opzionalmente filtrate per stato),
 * proiettate in {@link TournamentSummaryDto}. Vengono restituite solo
 * le righe non eliminate.
 *
 * @see ListTournamentSummariesUseCase
 * @see TournamentSummaryLocalRepository
 * @see TournamentSummarySyncService
 */
@Service
@Transactional(readOnly = true)
public class ListTournamentSummariesService implements ListTournamentSummariesUseCase {

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;

    /**
     * Costruisce il servizio con il repository dei riepiloghi dei tornei.
     *
     * @param tournamentSummaryLocalRepository il repository per l'accesso ai riepiloghi (non null)
     */
    public ListTournamentSummariesService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
    }

    /**
     * Restituisce la lista dei riepiloghi dei tornei, opzionalmente
     * filtrati per stato. Le righe eliminate vengono escluse.
     *
     * @param statusFilter filtro opzionale per stato del torneo (null per nessun filtro)
     * @return la lista dei DTO di riepilogo dei tornei
     */
    @Override
    public List<TournamentSummaryDto> listSummaries(TournamentStatus statusFilter) {
        return tournamentSummaryLocalRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .filter(s -> statusFilter == null || s.getStatus() == statusFilter)
                .map(ListTournamentSummariesService::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Converte un {@link TournamentSummaryLocal} nel corrispondente
     * {@link TournamentSummaryDto}.
     *
     * @param summary il riepilogo del torneo dal modello di dominio (non null)
     * @return il DTO con tutti i campi mappati uno-a-uno
     */
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