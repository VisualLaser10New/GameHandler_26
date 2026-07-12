package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentParticipantsLocalSyncService;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of {@link InternalTournamentSummaryController}.
 * Receives batches of {@code TOURNAMENT_PARTICIPANTS_UPSERTED} events
 * replicated by the Central System and delegates them to
 * {@link TournamentParticipantsLocalSyncService#applyEvents} for an
 * idempotent full-snapshot replace (delete+insert by {@code tournamentId}) on
 * {@code tournament_participants_local}.
 */
@RestController
@RequestMapping("/internal/tournaments/participants")
public class InternalTournamentParticipantsController {

    private final TournamentParticipantsLocalSyncService syncService;

    public InternalTournamentParticipantsController(TournamentParticipantsLocalSyncService syncService) {
        this.syncService = syncService;
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentParticipants(@RequestBody List<TournamentParticipantsEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}