package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.GameDefinitionSyncService;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Receives batches of {@code GAME_DEFINITION_UPSERTED} metadata events replicated
 * by the Central System. Security is enforced by {@code InternalApiKeyFilter}
 * for every {@code /internal/**} path via the {@code X-Internal-Api-Key} header.
 *
 * <p>This controller is the Local counterpart of the Central {@code LocalGameDefinitionRestAdapter},
 * mirroring the FASE 1 {@code InternalMetadataController} that handles
 * {@code LOCAL_ADMIN_BUILDING_*} events.</p>
 */
@RestController
@RequestMapping("/internal/metadata/game-definitions")
public class InternalGameDefinitionSyncController {

    private final GameDefinitionSyncService gameDefinitionSyncService;

    /**
     * Costruisce il controller con il servizio di sincronizzazione delle
     * definizioni di gioco.
     *
     * @param gameDefinitionSyncService servizio per l'applicazione degli eventi di definizione
     */
    public InternalGameDefinitionSyncController(GameDefinitionSyncService gameDefinitionSyncService) {
        this.gameDefinitionSyncService = gameDefinitionSyncService;
    }

    /**
     * Riceve un batch di eventi di definizione di gioco replicati dal
     * sistema centrale e li applica al database locale.
     *
     * @param events la lista degli eventi di definizione di gioco
     * @return una {@link ResponseEntity} con status 200
     */
    @PutMapping("/sync")
    public ResponseEntity<Void> syncGameDefinitions(@RequestBody List<GameDefinitionEventDto> events) {
        gameDefinitionSyncService.applyEvents(events);
        return ResponseEntity.ok().build();
    }
}
