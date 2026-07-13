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

    public InternalTeamMembersController(TeamMembersLocalSyncService syncService) {
        this.syncService = syncService;
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncTeamMembers(@RequestBody List<TeamMembersEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}