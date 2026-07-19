package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.RegisterTournamentParticipantRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.RegisterTournamentParticipantDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * PLAYER write endpoint (PIANO §7.B W6): a PLAYER registers as a
 * tournament participant. The {@code @PreAuthorize("hasRole('PLAYER')")}
 * enforces the role at the Spring Security layer; the use case
 * additionally pre-controls the role on {@code replicated_users}. The
 * resulting {@link AdminRequestDto} is returned with status
 * {@code PENDING} (the outbox row has been written and is awaiting
 * Central async processing).
 */
@RestController
@RequestMapping("/api/tournaments")
@PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
public class PlayerTournamentRegistrationController {

    private final RegisterTournamentParticipantRequestedUseCase registerUseCase;
    private final CurrentUserService currentUserService;
    private final String buildingId;

    /**
     * Costruisce il controller con il caso d'uso per la registrazione ai
     * tornei e il servizio per l'utente corrente.
     *
     * @param registerUseCase caso d'uso per la registrazione partecipante
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     * @param buildingId identificativo dell'edificio
     */
    public PlayerTournamentRegistrationController(RegisterTournamentParticipantRequestedUseCase registerUseCase,
                                                   CurrentUserService currentUserService,
                                                   @Value("${app.building-id}") String buildingId) {
        this.registerUseCase = registerUseCase;
        this.currentUserService = currentUserService;
        this.buildingId = buildingId;
    }

    /**
     * Registra l'utente autenticato come partecipante a un torneo,
     * opzionalmente come team. La richiesta viene processata in modo
     * asincrono tramite outbox.
     *
     * @param id l'identificativo del torneo
     * @param body i dati di registrazione (opzionale, per registrazione di team)
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<AdminRequestDto> register(@PathVariable String id,
                                                     @RequestBody(required = false) RegisterTournamentParticipantDto body) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String teamName = null;
        java.util.List<String> teamMemberIds = null;
        if (body != null) {
            teamName = body.teamName();
            teamMemberIds = body.teamMembers();
        }
        AdminRequestDto result = registerUseCase.register(
                id,
                currentUserId.get().value(),
                "PLAYER",
                buildingId,
                teamName,
                teamMemberIds
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}