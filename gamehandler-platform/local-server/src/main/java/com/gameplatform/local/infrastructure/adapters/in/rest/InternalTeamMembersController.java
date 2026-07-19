package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TeamMembersLocalSyncService;
import com.gameplatform.shared.dto.TeamMembersEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of {@link InternalTournamentParticipantsController}.
 * Receives batches of {@code TEAM_MEMBERS_UPSERTED} events replicated by the
 * Central System (BUG-TEAM-3) and delegates them to
 * {@link TeamMembersLocalSyncService#applyEvents} for an idempotent
 * full-snapshot replace (delete+insert by {@code tournamentId}) on
 * {@code team_members_local}.
 */
@RestController
@RequestMapping("/internal/tournaments/teams/members")
public class InternalTeamMembersController {

    private final TeamMembersLocalSyncService syncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione dei
     * membri del team.
     *
     * @param syncService servizio per l'applicazione degli eventi dei membri del team
     */
    public InternalTeamMembersController(TeamMembersLocalSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Riceve un batch di eventi di membri del team replicati dal sistema
     * centrale e li applica al database locale.
     *
     * @param events la lista degli eventi dei membri del team
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncTeamMembers(@RequestBody List<TeamMembersEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}