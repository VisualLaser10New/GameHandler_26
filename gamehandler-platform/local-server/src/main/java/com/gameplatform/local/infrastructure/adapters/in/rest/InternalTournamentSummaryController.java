package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentSummarySyncService;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of the existing
 * {@code InternalGameDefinitionSyncController} and
 * {@code InternalTournamentController} (the FASE 6 tournament-match sync
 * endpoint). Receives batches of {@code TOURNAMENT_SUMMARY_UPSERTED} events
 * replicated by the Central System and delegates them to
 * {@link TournamentSummarySyncService#applyEvents} for an idempotent upsert by
 * PK {@code tournamentId} on {@code tournaments_summary_local}.
 */
@RestController
@RequestMapping("/internal/tournaments/summaries")
public class InternalTournamentSummaryController {

    private final TournamentSummarySyncService tournamentSummarySyncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione dei
     * riepiloghi dei tornei.
     *
     * @param tournamentSummarySyncService servizio per l'applicazione degli eventi
     */
    public InternalTournamentSummaryController(TournamentSummarySyncService tournamentSummarySyncService) {
        this.tournamentSummarySyncService = tournamentSummarySyncService;
    }

    /**
     * Riceve un batch di eventi di riepilogo torneo replicati dal sistema
     * centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di riepilogo torneo
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentSummaries(@RequestBody List<TournamentSummaryEventDto> events) {
        tournamentSummarySyncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}
