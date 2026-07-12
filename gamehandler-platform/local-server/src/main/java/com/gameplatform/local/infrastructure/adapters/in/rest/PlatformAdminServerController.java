package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.GetLocalServerHealthViewUseCase;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLATFORM_ADMIN read endpoint (PIANO §7.B): returns the local-server
 * health view aggregating this node's own pending-outbox count with the
 * full registry of all known registered local servers (replicated via
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED}). The
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')} enforces the role at
 * the Spring Security layer.
 */
@RestController
@RequestMapping("/api/admin/servers")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminServerController {

    private final GetLocalServerHealthViewUseCase getLocalServerHealthViewUseCase;

    public PlatformAdminServerController(GetLocalServerHealthViewUseCase getLocalServerHealthViewUseCase) {
        this.getLocalServerHealthViewUseCase = getLocalServerHealthViewUseCase;
    }

    @GetMapping("/health")
    public ResponseEntity<ServerHealthViewDto> getHealth() {
        return ResponseEntity.ok(getLocalServerHealthViewUseCase.getHealthView());
    }
}