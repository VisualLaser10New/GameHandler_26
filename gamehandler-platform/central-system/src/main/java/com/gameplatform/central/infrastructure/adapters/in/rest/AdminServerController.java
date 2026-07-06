package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.dto.ServerHealthDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M12 — admin REST adapter that exposes the current health of every registered
 * local server.
 *
 * <p>{@code GET /internal/servers} returns one {@link ServerHealthDto} per
 * registered server, ordered by {@code lastSeenAt} descending (newest heartbeat
 * first). The DTO combines the registry snapshot (active flag, baseUrl,
 * lastSeenAt) with the per-server pending-replication backlog from the
 * outbox / replication-progress tables.</p>
 *
 * <p>Security: the endpoint lives under {@code /internal/**} and is therefore
 * guarded by
 * {@link com.gameplatform.central.infrastructure.security.InternalApiKeyFilter},
 * which rejects requests without a valid {@code X-Internal-Api-Key} header with
 * {@code 403 Forbidden}. The filter uses {@code path.startsWith("/internal/")}
 * so {@code /internal/servers} is covered automatically — no extra path pattern
 * is required.</p>
 *
 * <p>The controller is intentionally thin: it only delegates to the domain
 * ports and assembles the DTO. No business logic lives here.</p>
 */
@RestController
public class AdminServerController {

    private final LocalServerRegistryPort localServerRegistryPort;
    private final OutboxEventRepository outboxEventRepository;

    public AdminServerController(LocalServerRegistryPort localServerRegistryPort,
                                 OutboxEventRepository outboxEventRepository) {
        this.localServerRegistryPort = localServerRegistryPort;
        this.outboxEventRepository = outboxEventRepository;
    }

    @GetMapping("/internal/servers")
    public ResponseEntity<List<ServerHealthDto>> listServers() {
        List<RegisteredLocalServer> servers = localServerRegistryPort.findAll();
        List<ServerHealthDto> body = servers.stream()
                .map(server -> new ServerHealthDto(
                        server.getBuildingId().id(),
                        server.getBaseUrl(),
                        server.getLastSeenAt(),
                        server.isActive(),
                        outboxEventRepository.countPendingReplicationForServer(server.getBuildingId().id())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }
}
