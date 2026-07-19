package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentMatchLocalSyncService;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of the existing
 * {@code InternalMetadataController} / {@code InternalGameDefinitionSyncController}.
 */
@RestController
@RequestMapping("/internal/tournaments/matches")
public class InternalTournamentController {

    private final TournamentMatchLocalSyncService tournamentMatchLocalSyncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione delle
     * partite torneo.
     *
     * @param tournamentMatchLocalSyncService servizio per l'applicazione degli eventi
     */
    public InternalTournamentController(TournamentMatchLocalSyncService tournamentMatchLocalSyncService) {
        this.tournamentMatchLocalSyncService = tournamentMatchLocalSyncService;
    }

    /**
     * Riceve un batch di eventi di partite torneo programmate replicati
     * dal sistema centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di partite torneo
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentMatches(@RequestBody List<TournamentMatchScheduledDto> events) {
        tournamentMatchLocalSyncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}