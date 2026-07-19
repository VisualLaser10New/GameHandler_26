package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.CreateTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.in.DeleteTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.in.TournamentLifecycleRequestedUseCase;
import com.gameplatform.local.domain.ports.in.UpdateTournamentRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.UpdateTournamentRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * PLATFORM_ADMIN write endpoints (PIANO §7.B W12a-f): create, open/cancel/
 * schedule, update and delete a tournament via the async outbox flow. Each
 * call pre-controls the {@code PLATFORM_ADMIN} role on {@code replicated_users}
 * (defense-in-depth on top of {@code @PreAuthorize}); W12e (update) and
 * W12f (delete) additionally pre-check the DRAFT invariant on
 * {@code tournaments_summary_local} and refuse immediately with a
 * {@code FAILED} admin-request (without writing the outbox row) when the
 * tournament is not DRAFT. The
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')} enforces the role at the
 * Spring Security layer.
 */
@RestController
@RequestMapping("/api/admin/tournaments")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminTournamentController {

    private static final String ROLE = "PLATFORM_ADMIN";

    private final CreateTournamentRequestedUseCase createUseCase;
    private final TournamentLifecycleRequestedUseCase lifecycleUseCase;
    private final UpdateTournamentRequestedUseCase updateUseCase;
    private final DeleteTournamentRequestedUseCase deleteUseCase;
    private final CurrentUserService currentUserService;
    private final String buildingId;

    /**
     * Costruisce il controller con i casi d'uso per la gestione del ciclo
     * di vita dei tornei e il servizio per l'utente corrente.
     *
     * @param createUseCase caso d'uso per la creazione di un torneo
     * @param lifecycleUseCase caso d'uso per le azioni sul ciclo di vita
     * @param updateUseCase caso d'uso per l'aggiornamento di un torneo
     * @param deleteUseCase caso d'uso per l'eliminazione di un torneo
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     * @param buildingId identificativo dell'edificio
     */
    public PlatformAdminTournamentController(CreateTournamentRequestedUseCase createUseCase,
                                              TournamentLifecycleRequestedUseCase lifecycleUseCase,
                                              UpdateTournamentRequestedUseCase updateUseCase,
                                              DeleteTournamentRequestedUseCase deleteUseCase,
                                              CurrentUserService currentUserService,
                                              @Value("${app.building-id}") String buildingId) {
        this.createUseCase = createUseCase;
        this.lifecycleUseCase = lifecycleUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.currentUserService = currentUserService;
        this.buildingId = buildingId;
    }

    /**
     * Crea un nuovo torneo. La richiesta viene processata in modo asincrono
     * tramite outbox.
     *
     * @param req i dati del torneo da creare
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PostMapping
    public ResponseEntity<AdminRequestDto> create(@RequestBody CreateTournamentRequestDto req) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminRequestDto result = createUseCase.create(
                req.name(), req.gameType(), req.teamBased(), req.teamSize(),
                req.startsAt(), req.buildingIds(),
                currentUserId.get().value(), ROLE, buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Esegue un'azione sul ciclo di vita di un torneo (open, cancel, schedule).
     *
     * @param id l'identificativo del torneo
     * @param action l'azione da eseguire ("open", "cancel", "schedule")
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     * @throws IllegalArgumentException se l'azione non è supportata
     */
    @PostMapping("/{id}/{action}")
    public ResponseEntity<AdminRequestDto> lifecycle(@PathVariable String id,
                                                      @PathVariable String action) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String eventType;
        switch (action.toLowerCase()) {
            case "open":
                eventType = "TOURNAMENT_OPEN_REQUESTED";
                break;
            case "cancel":
                eventType = "TOURNAMENT_CANCEL_REQUESTED";
                break;
            case "schedule":
                eventType = "TOURNAMENT_SCHEDULE_REQUESTED";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported tournament lifecycle action: '" + action
                        + "'. Valid values are: open, cancel, schedule");
        }
        AdminRequestDto result = lifecycleUseCase.lifecycle(
                eventType, id, currentUserId.get().value(), ROLE, buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Aggiorna un torneo esistente. La richiesta viene processata in modo
     * asincrono tramite outbox.
     *
     * @param id l'identificativo del torneo
     * @param req i dati aggiornati del torneo
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @PutMapping("/{id}")
    public ResponseEntity<AdminRequestDto> update(@PathVariable String id,
                                                    @RequestBody UpdateTournamentRequestDto req) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminRequestDto result = updateUseCase.update(
                id, req.name(), req.startsAt(), req.buildingIds(),
                currentUserId.get().value(), ROLE, buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Elimina un torneo esistente. La richiesta viene processata in modo
     * asincrono tramite outbox.
     *
     * @param id l'identificativo del torneo
     * @return una {@link ResponseEntity} con status 202 e il {@link AdminRequestDto}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<AdminRequestDto> delete(@PathVariable String id) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminRequestDto result = deleteUseCase.delete(
                id, currentUserId.get().value(), ROLE, buildingId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}