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

    public SyncController(ReceiveSyncDataUseCase receiveSyncDataUseCase,
                          LocalServerRegistryPort localServerRegistryPort) {
        this.receiveSyncDataUseCase = receiveSyncDataUseCase;
        this.localServerRegistryPort = localServerRegistryPort;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync receive
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Receives a batch of outbox events from a local server.
     * As a side-effect the server's {@code lastSeenAt} is updated (heartbeat).
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
     * Registers or updates the local server identified by the given building.
     * If the server already exists in the registry its {@code baseUrl} and
     * {@code lastSeenAt} are refreshed; otherwise a new entry is created.
     *
     * @param request body containing {@code buildingId} and {@code baseUrl}
     * @return 200 OK on success
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
     * Request body for the server-registration endpoint.
     */
    public record RegisterServerRequest(
            @NotBlank(message = "buildingId must not be blank")
            String buildingId,

            @NotBlank(message = "baseUrl must not be blank")
            String baseUrl
    ) {}
}
