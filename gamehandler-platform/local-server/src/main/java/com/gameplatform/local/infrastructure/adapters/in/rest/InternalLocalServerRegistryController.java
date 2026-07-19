package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.RegisteredLocalServerSyncService;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of {@link InternalTournamentSummaryController}.
 * Receives batches of {@code LOCAL_SERVER_REGISTRY_UPSERTED} events
 * replicated by the Central System and delegates them to
 * {@link RegisteredLocalServerSyncService#applyEvents} for an idempotent
 * upsert by PK {@code buildingId} on {@code registered_local_servers_local}.
 */
@RestController
@RequestMapping("/internal/servers")
public class InternalLocalServerRegistryController {

    private final RegisteredLocalServerSyncService syncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione del
     * registro dei server locali.
     *
     * @param syncService servizio per l'applicazione degli eventi di registro
     */
    public InternalLocalServerRegistryController(RegisteredLocalServerSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Riceve un batch di eventi di registro dei server locali replicati
     * dal sistema centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di registro server
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncLocalServerRegistry(@RequestBody List<LocalServerRegistryEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}