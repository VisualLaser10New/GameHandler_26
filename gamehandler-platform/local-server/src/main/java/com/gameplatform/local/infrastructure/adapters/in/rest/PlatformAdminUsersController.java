package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.ListUsersDirectoryUseCase;
import com.gameplatform.shared.dto.UsersDirectoryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PLATFORM_ADMIN read endpoint (PIANO §7.B, deviation D1): returns the
 * directory projection of all locally replicated users (excluding the
 * {@code hashedPassword}). The {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')}
 * enforces the role at the Spring Security layer.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminUsersController {

    private final ListUsersDirectoryUseCase listUsersDirectoryUseCase;

    /**
     * Costruisce il controller con il caso d'uso per la consultazione
     * della directory utenti.
     *
     * @param listUsersDirectoryUseCase caso d'uso per la lista degli utenti
     */
    public PlatformAdminUsersController(ListUsersDirectoryUseCase listUsersDirectoryUseCase) {
        this.listUsersDirectoryUseCase = listUsersDirectoryUseCase;
    }

    /**
     * Restituisce la directory di tutti gli utenti replicati localmente,
     * escludendo le password hashate.
     *
     * @return una {@link ResponseEntity} con la lista di {@link UsersDirectoryDto}
     */
    @GetMapping
    public ResponseEntity<List<UsersDirectoryDto>> listUsers() {
        return ResponseEntity.ok(listUsersDirectoryUseCase.listAllUsers());
    }
}