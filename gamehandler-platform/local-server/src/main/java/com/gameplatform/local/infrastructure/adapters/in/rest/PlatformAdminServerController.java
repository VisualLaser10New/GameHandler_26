package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.in.GetLocalServerHealthViewUseCase;
import com.gameplatform.local.domain.ports.in.ToggleLocalServerActiveUseCase;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import com.gameplatform.shared.dto.ToggleServerActiveRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * PLATFORM_ADMIN read endpoint (PIANO §7.B): returns the local-server
 * health view aggregating this node's own pending-outbox count with the
 * full registry of all known registered local servers (replicated via
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED}). The
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')} enforces the role at
 * the Spring Security layer.
 * <p>
 * Feature 3 — adds the {@code PATCH /api/admin/servers/{buildingId}/active}
 * write endpoint that toggles the {@code active} flag of the locally
 * replicated {@code registered_local_servers_local} row.
 */
@RestController
@RequestMapping("/api/admin/servers")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminServerController {

    private final GetLocalServerHealthViewUseCase getLocalServerHealthViewUseCase;
    private final ToggleLocalServerActiveUseCase toggleLocalServerActiveUseCase;

    public PlatformAdminServerController(GetLocalServerHealthViewUseCase getLocalServerHealthViewUseCase,
                                         ToggleLocalServerActiveUseCase toggleLocalServerActiveUseCase) {
        this.getLocalServerHealthViewUseCase = getLocalServerHealthViewUseCase;
        this.toggleLocalServerActiveUseCase = toggleLocalServerActiveUseCase;
    }

    @GetMapping("/health")
    public ResponseEntity<ServerHealthViewDto> getHealth() {
        return ResponseEntity.ok(getLocalServerHealthViewUseCase.getHealthView());
    }

    @PatchMapping("/{buildingId}/active")
    public ResponseEntity<ServerHealthDto> toggleActive(@PathVariable String buildingId,
                                                        @RequestBody ToggleServerActiveRequestDto req) {
        Optional<RegisteredLocalServerLocal> updated = toggleLocalServerActiveUseCase.setActive(buildingId, req.active());
        return updated
                .map(s -> ResponseEntity.ok(toDto(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static ServerHealthDto toDto(RegisteredLocalServerLocal s) {
        return new ServerHealthDto(
                s.getBuildingId().id(),
                s.getBaseUrl(),
                s.getLastSeenAt(),
                s.isActive(),
                0L);
    }
}