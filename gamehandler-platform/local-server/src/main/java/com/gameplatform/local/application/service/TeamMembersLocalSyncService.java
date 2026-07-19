package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.out.TeamMembersLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TeamMemberEntryDto;
import com.gameplatform.shared.dto.TeamMembersEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Riceve eventi {@code TEAM_MEMBERS_UPSERTED} replicati dal Central
 * tramite outbox e li applica idempotentemente alla tabella
 * {@code team_members_local}. L'evento trasporta uno snapshot completo
 * delle appartenenze team→utente per torneo; il servizio sostituisce
 * la proiezione locale atomicamente (delete by tournamentId + insert
 * di ogni entry). Le righe aggiornate alimentano la subquery EXISTS
 * per il {@code myMatches} del giocatore.
 *
 * <p>Non gestisce {@code markCompletedIfRequested}: la chiusura della
 * richiesta admin per la registrazione e' guidata dall'evento di ritorno
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} separato.</p>
 *
 * @see TeamMembersLocalRepository
 */
@Service
@Transactional
public class TeamMembersLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeamMembersLocalSyncService.class);

    static final String EVENT_TEAM_MEMBERS_UPSERTED = "TEAM_MEMBERS_UPSERTED";

    private final TeamMembersLocalRepository teamMembersLocalRepository;

    /**
     * Costruisce il servizio con il repository delle membership squadra.
     *
     * @param teamMembersLocalRepository il repository per l'accesso alle
     *                                   membership squadra locali (non null)
     */
    public TeamMembersLocalSyncService(TeamMembersLocalRepository teamMembersLocalRepository) {
        this.teamMembersLocalRepository = teamMembersLocalRepository;
    }

    /**
     * Applica una lista di eventi di membership squadra alla tabella
     * locale. Per ogni evento TEAM_MEMBERS_UPSERTED, sostituisce
     * atomicamente la proiezione locale (delete + insert).
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
    public void applyEvents(List<TeamMembersEventDto> events) {
        if (events == null) {
            return;
        }
        for (TeamMembersEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TEAM_MEMBERS_UPSERTED.equals(eventType)) {
                log.warn("Unknown team-members event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Team-members event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            // Replace the local snapshot atomically (full-snapshot idempotency).
            teamMembersLocalRepository.deleteByTournament(tournamentId);
            int inserted = 0;
            if (event.teams() != null) {
                for (TeamMemberEntryDto entry : event.teams()) {
                    if (entry == null || entry.teamId() == null || entry.teamId().isBlank()) {
                        continue;
                    }
                    if (entry.teamMembers() != null) {
                        for (String userId : entry.teamMembers()) {
                            if (userId == null || userId.isBlank()) {
                                continue;
                            }
                            teamMembersLocalRepository.save(event.tournamentId(), entry.teamId(), userId);
                            inserted++;
                        }
                    }
                }
            }
            log.info("Team-members event [{}] replaced projection for tournament {} ({} memberships)",
                    event.eventId(), tournamentId.value(), inserted);
        }
    }
}