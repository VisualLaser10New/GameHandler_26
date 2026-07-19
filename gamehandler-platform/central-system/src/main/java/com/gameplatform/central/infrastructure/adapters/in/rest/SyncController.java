package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.SyncPayloadDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST adapter for internal server synchronisation and self-registration.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code POST /internal/sync/receive}         — receives a batch of events from a local server</li>
 *   <li>{@code POST /internal/servers/register}     — explicitly registers (or updates) a local server</li>
 * </ul>
 * </p>
 *
 * <p>All endpoints are protected by {@link com.gameplatform.central.infrastructure.security.InternalApiKeyFilter}.</p>
 */
@RestController
public class SyncController {

    private final ReceiveSyncDataUseCase receiveSyncDataUseCase;
    private final LocalServerRegistryPort localServerRegistryPort;

    /**
     * Costruisce il controller iniettando i port di dominio necessari.
     *
     * @param receiveSyncDataUseCase caso d'uso per l'elaborazione dei dati di sincronizzazione, non {@code null}
     * @param localServerRegistryPort port di uscita per la registrazione dei server locali, non {@code null}
     */
    public SyncController(ReceiveSyncDataUseCase receiveSyncDataUseCase,
                           LocalServerRegistryPort localServerRegistryPort) {
        this.receiveSyncDataUseCase = receiveSyncDataUseCase;
        this.localServerRegistryPort = localServerRegistryPort;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync receive
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Riceve un batch di eventi outbox provenienti da un server locale.
     *
     * <p>Come effetto collaterale aggiorna l'istante dell'ultimo heartbeat
     * ({@code lastSeenAt}) del server che ha inviato i dati.</p>
     *
     * @param payload dto contenente il batch di eventi da sincronizzare, non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e corpo vuoto
     */
    @PostMapping("/internal/sync/receive")
    public ResponseEntity<Void> receiveSync(@RequestBody SyncPayloadDto payload) {
        receiveSyncDataUseCase.receiveSyncPayload(payload);
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Explicit server registration
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registra o aggiorna il server locale identificato dall'edificio indicato.
     *
     * <p>Se il server è già presente nel registro ne aggiorna l'URL di base e
     * l'istante dell'ultimo heartbeat; in caso contrario ne crea una nuova entry
     * marcandolo come attivo.</p>
     *
     * @param request corpo della richiesta contenente {@code buildingId} e {@code baseUrl},
     *                validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e corpo vuoto
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     */
    @PostMapping("/internal/servers/register")
    public ResponseEntity<Void> registerServer(@Valid @RequestBody RegisterServerRequest request) {
        BuildingId buildingId = new BuildingId(request.buildingId());
        RegisteredLocalServer server = new RegisteredLocalServer(
                buildingId,
                request.baseUrl(),
                Instant.now(),
                true
        );
        localServerRegistryPort.register(server);
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Inner request DTO (controller-local, not shared across modules)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Corpo di richiesta per l'endpoint di registrazione di un server locale.
     *
     * @param buildingId identificativo dell'edificio associato al server, non vuoto
     * @param baseUrl    URL di base del server locale, non vuoto
     */
    public record RegisterServerRequest(
            @NotBlank(message = "buildingId must not be blank")
            String buildingId,

            @NotBlank(message = "baseUrl must not be blank")
            String baseUrl
    ) {}
}
