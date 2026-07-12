package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentStandingsLocalSyncService;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of {@link InternalTournamentSummaryController}.
 * Receives batches of {@code TOURNAMENT_STANDINGS_UPSERTED} events
 * replicated by the Central System and delegates them to
 * {@link TournamentStandingsLocalSyncService#applyEvents} for an idempotent
 * full-snapshot replace (delete+insert by {@code tournamentId}) on
 * {@code tournament_standings_local}.
 */
@RestController
@RequestMapping("/internal/tournaments/standings")
public class InternalTournamentStandingsController {

    private final TournamentStandingsLocalSyncService syncService;

    public InternalTournamentStandingsController(TournamentStandingsLocalSyncService syncService) {
        this.syncService = syncService;
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentStandings(@RequestBody List<TournamentStandingsEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}