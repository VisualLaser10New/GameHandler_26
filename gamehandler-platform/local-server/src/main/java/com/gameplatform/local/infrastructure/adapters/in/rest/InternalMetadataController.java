package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.LocalAdminBuildingSyncService;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local-server REST endpoint that receives LOCAL_ADMIN↔building metadata events
 * replicated from the Central via outbox and forwards them to
 * {@link LocalAdminBuildingSyncService} for idempotent upsert/delete on the
 * {@code local_admin_buildings_local} table.
 *
 * <p>The base path is {@code /internal/metadata}, so the endpoint is covered by
 * {@link com.gameplatform.local.infrastructure.security.InternalApiKeyFilter}
 * (it intercepts every path starting with {@code /internal/} and validates the
 * {@code X-Internal-Api-Key} header). No {@code @PreAuthorize} is declared and
 * the API key is NOT re-validated in the controller, mirroring the
 * {@code InternalSyncController} convention.
 */
@RestController
@RequestMapping("/internal/metadata")
public class InternalMetadataController {

    private final LocalAdminBuildingSyncService localAdminBuildingSyncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione delle
     * associazioni admin-edificio.
     *
     * @param localAdminBuildingSyncService servizio per l'applicazione degli eventi
     */
    public InternalMetadataController(LocalAdminBuildingSyncService localAdminBuildingSyncService) {
        this.localAdminBuildingSyncService = localAdminBuildingSyncService;
    }

    /**
     * Riceve un batch di eventi di associazione admin-edificio replicati
     * dal sistema centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di associazione admin-edificio
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncMetadata(@RequestBody List<LocalAdminBuildingEventDto> events) {
        localAdminBuildingSyncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}