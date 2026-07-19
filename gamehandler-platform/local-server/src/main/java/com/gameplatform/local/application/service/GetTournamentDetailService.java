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
 * Caso d'uso in lettura (PIANO §7.B): aggrega la vista di dettaglio
 * di un singolo torneo (riepilogo + classifiche + incontri + partecipanti)
 * a partire dalle quattro repliche locali. Restituisce vuoto se la riga
 * di riepilogo del torneo e' assente o marcata come eliminata.
 *
 * @see GetTournamentDetailUseCase
 * @see TournamentSummaryLocalRepository
 * @see TournamentStandingsLocalRepository
 * @see TournamentParticipantsLocalRepository
 * @see TournamentMatchLocalRepository
 */
@Service
@Transactional(readOnly = true)
public class GetTournamentDetailService implements GetTournamentDetailUseCase {

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final TournamentStandingsLocalRepository tournamentStandingsLocalRepository;
    private final TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository;
    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;

    /**
     * Costruisce il servizio con i quattro repository locali necessari
     * per aggregare la vista di dettaglio del torneo.
     *
     * @param tournamentSummaryLocalRepository     il repository delle repliche riepilogo torneo
     * @param tournamentStandingsLocalRepository   il repository delle repliche classifiche torneo
     * @param tournamentParticipantsLocalRepository il repository delle repliche partecipanti torneo
     * @param tournamentMatchLocalRepository       il repository delle repliche match torneo
     */
    public GetTournamentDetailService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                       TournamentStandingsLocalRepository tournamentStandingsLocalRepository,
                                       TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository,
                                       TournamentMatchLocalRepository tournamentMatchLocalRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.tournamentStandingsLocalRepository = tournamentStandingsLocalRepository;
        this.tournamentParticipantsLocalRepository = tournamentParticipantsLocalRepository;
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
    }

    /**
     * Recupera il dettaglio completo di un torneo, aggregando riepilogo,
     * classifiche, incontri e partecipanti. Restituisce vuoto se il
     * torneo non esiste o e' marcato come eliminato.
     *
     * @param tournamentId l'identificativo del torneo (non blank per avere un risultato)
     * @return un Optional contenente il dettaglio del torneo, o vuoto se non trovato o eliminato
     */
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

    /**
     * Converte un {@link TournamentSummaryLocal} nel corrispondente
     * {@link TournamentSummaryDto}.
     *
     * @param summary il riepilogo del torneo dal modello di dominio (non null)
     * @return il DTO con tutti i campi mappati uno-a-uno
     */
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

    /**
     * Converte un {@link TournamentStandingLocal} nel corrispondente
     * {@link TournamentStandingDto}.
     *
     * @param standing la classifica del torneo dal modello di dominio (non null)
     * @return il DTO con participantId, displayName, wins, losses, points e rank
     */
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

    /**
     * Converte un {@link TournamentParticipantLocal} nel corrispondente
     * {@link TournamentParticipantViewDto}.
     *
     * @param participant il partecipante del torneo dal modello di dominio (non null)
     * @return il DTO con participantId, isTeam, displayName e registeredAt
     */
    private static TournamentParticipantViewDto toParticipantView(TournamentParticipantLocal participant) {
        return new TournamentParticipantViewDto(
                participant.getParticipantId(),
                participant.isTeam(),
                participant.getDisplayName(),
                participant.getRegisteredAt()
        );
    }

    /**
     * Converte un {@link TournamentMatchLocal} nel corrispondente
     * {@link TournamentMatchDto}. I campi risultato e vincitore vengono
     * impostati a null per la vista di dettaglio.
     *
     * @param match il match del torneo dal modello di dominio (non null)
     * @return il DTO con id, round, bracketPosition, partecipanti, gameId, status e scheduledAt
     */
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