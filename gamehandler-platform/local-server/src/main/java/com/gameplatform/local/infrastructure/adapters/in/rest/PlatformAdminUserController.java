package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.AssignRoleRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * PLATFORM_ADMIN write endpoint (PIANO §7.B W10, RF-UT-02): a
 * PLATFORM_ADMIN assigns a new role set to a target user. The
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')} enforces the role at
 * the Spring Security layer; the use case additionally pre-controls the
 * role on {@code replicated_users}.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminUserController {

    private final AssignRoleRequestedUseCase assignUseCase;
    private final CurrentUserService currentUserService;
    private final String buildingId;

    /**
     * Costruisce il controller con il caso d'uso per l'assegnazione dei
     * ruoli e il servizio per l'utente corrente.
     *
     * @param assignUseCase caso d'uso per l'assegnazione dei ruoli
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     * @param buildingId identificativo dell'edificio
     */
    public PlatformAdminUserController(AssignRoleRequestedUseCase assignUseCase,
                                       CurrentUserService currentUserService,
                                       @Value("${app.building-id}") String buildingId) {
        this.assignUseCase = assignUseCase;
        this.currentUserService = currentUserService;
        this.buildingId = buildingId;
    }

    /**
     * Assegna un nuovo insieme di ruoli a un utente target. La richiesta
     * viene processata in modo asincrono tramite outbox.
     *
     * @param userId l'identificativo dell'utente target
     * @param roles la lista dei ruoli da assegnare
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PostMapping("/{userId}/roles")
    public ResponseEntity<AdminRequestDto> assignRoles(@PathVariable String userId,
                                                       @RequestBody List<String> roles) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminRequestDto result = assignUseCase.assign(
                userId,
                roles,
                currentUserId.get().value(),
                "PLATFORM_ADMIN",
                buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}