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

    public InternalLocalServerRegistryController(RegisteredLocalServerSyncService syncService) {
        this.syncService = syncService;
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncLocalServerRegistry(@RequestBody List<LocalServerRegistryEventDto> events) {
        syncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}