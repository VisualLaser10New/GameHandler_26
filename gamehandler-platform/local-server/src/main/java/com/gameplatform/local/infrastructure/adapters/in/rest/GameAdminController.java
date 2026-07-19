package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.UpsertGameDefinitionRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.UpsertGameDefinitionRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * GAME_ADMIN write endpoints (PIANO §7.B W9): a GAME_ADMIN upserts a
 * game definition (POST create a new one or PUT update an existing one
 * keyed by {@code gameType}). The {@code @PreAuthorize("hasRole('GAME_ADMIN')")}
 * enforces the role at the Spring Security layer; the use case
 * additionally pre-controls the role on {@code replicated_users}.
 */
@RestController
@RequestMapping("/api/admin/games")
@PreAuthorize("hasRole('GAME_ADMIN') or hasRole('PLATFORM_ADMIN')")
public class GameAdminController {

    private final UpsertGameDefinitionRequestedUseCase upsertUseCase;
    private final CurrentUserService currentUserService;
    private final String buildingId;

    /**
     * Costruisce il controller con il caso d'uso per l'upsert delle definizioni
     * di gioco e il servizio per l'utente corrente.
     *
     * @param upsertUseCase caso d'uso per l'upsert delle definizioni di gioco
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     * @param buildingId identificativo dell'edificio
     */
    public GameAdminController(UpsertGameDefinitionRequestedUseCase upsertUseCase,
                                CurrentUserService currentUserService,
                                @Value("${app.building-id}") String buildingId) {
        this.upsertUseCase = upsertUseCase;
        this.currentUserService = currentUserService;
        this.buildingId = buildingId;
    }

    /**
     * Crea una nuova definizione di gioco. La richiesta viene processata in
     * modo asincrono tramite outbox; il risultato è un {@link AdminRequestDto}
     * con stato PENDING.
     *
     * @param req i dati della definizione di gioco da creare
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PostMapping
    public ResponseEntity<AdminRequestDto> createGame(@RequestBody UpsertGameDefinitionRequestDto req) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminRequestDto result = upsertUseCase.upsert(
                req.gameType(),
                req.name(),
                req.minPlayers(),
                req.maxPlayers(),
                req.teamAllowed(),
                req.registrationRules(),
                currentUserId.get().value(),
                "GAME_ADMIN",
                buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Aggiorna una definizione di gioco esistente, identificata dal
     * {@code gameType}. La richiesta viene processata in modo asincrono
     * tramite outbox.
     *
     * @param gameType il tipo di gioco da aggiornare (usato come fallback se
     *                 non specificato nel body)
     * @param req i dati aggiornati della definizione di gioco
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PutMapping("/{gameType}")
    public ResponseEntity<AdminRequestDto> updateGame(@PathVariable String gameType,
                                                       @RequestBody UpsertGameDefinitionRequestDto req) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        com.gameplatform.shared.domain.model.GameType resolvedType = req.gameType() != null
                ? req.gameType()
                : com.gameplatform.shared.domain.model.GameType.valueOf(gameType.toUpperCase());
        AdminRequestDto result = upsertUseCase.upsert(
                resolvedType,
                req.name(),
                req.minPlayers(),
                req.maxPlayers(),
                req.teamAllowed(),
                req.registrationRules(),
                currentUserId.get().value(),
                "GAME_ADMIN",
                buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}