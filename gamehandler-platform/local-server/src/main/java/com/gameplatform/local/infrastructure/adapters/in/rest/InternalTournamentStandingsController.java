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

    /**
     * Costruisce il controller con il servizio di sincronizzazione delle
     * classifiche dei tornei.
     *
     * @param syncService servizio per l'applicazione degli eventi di classifica
     */
    public InternalTournamentStandingsController(TournamentStandingsLocalSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Riceve un batch di eventi di classifiche torneo replicati dal sistema
     * centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di classifica
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentStandings(@RequestBody List<TournamentStandingsEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}