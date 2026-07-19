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

    /**
     * Costruisce il controller con il servizio di sincronizzazione dei
     * partecipanti ai tornei.
     *
     * @param syncService servizio per l'applicazione degli eventi dei partecipanti
     */
    public InternalTournamentParticipantsController(TournamentParticipantsLocalSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Riceve un batch di eventi di partecipanti ai tornei replicati dal
     * sistema centrale e li applica al database locale.
     *
     * @param events la lista degli eventi dei partecipanti
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentParticipants(@RequestBody List<TournamentParticipantsEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}