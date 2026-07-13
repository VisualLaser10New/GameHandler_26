package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.ports.in.ListTournamentParticipantsUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.UnregisterTournamentParticipantUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.RegisterTournamentParticipantDto;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter exposing the FASE 4 tournament registration endpoints (PIANO_UTENTI_TORNEI.md
 * §3.6 {@code TournamentRegistrationController}). Registration paths require
 * {@code PLAYER}; listing is {@code authenticated}.
 *
 * <p>The captain is the authenticated principal (locked decision C.4 — no
 * {@code captain} body field); the service validates that the captain is
 * contained in {@code teamMembers} and that the list has size {@code tournament.teamSize}.
 */
@RestController
@RequestMapping("/api/tournaments/{id}/participants")
public class TournamentRegistrationController {

    private final RegisterTournamentParticipantUseCase registerUseCase;
    private final UnregisterTournamentParticipantUseCase unregisterUseCase;
    private final ListTournamentParticipantsUseCase listUseCase;
    private final CurrentUserService currentUserService;

    public TournamentRegistrationController(RegisterTournamentParticipantUseCase registerUseCase,
                                            UnregisterTournamentParticipantUseCase unregisterUseCase,
                                            ListTournamentParticipantsUseCase listUseCase,
                                            CurrentUserService currentUserService) {
        this.registerUseCase = registerUseCase;
        this.unregisterUseCase = unregisterUseCase;
        this.listUseCase = listUseCase;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentParticipantDto> register(@PathVariable String id,
                                                             @Valid @RequestBody RegisterTournamentParticipantDto request) {
        UserId captainId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new InvalidTournamentException("Authenticated user could not be resolved"));
        return ResponseEntity.ok(registerUseCase.register(new TournamentId(id), captainId, request.teamName(), request.teamMembers()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        UserId uid = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new InvalidTournamentException("Authenticated user could not be resolved"));
        unregisterUseCase.unregister(new TournamentId(id), uid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<TournamentParticipantDto>> list(@PathVariable String id) {
        return ResponseEntity.ok(listUseCase.listParticipants(new TournamentId(id)));
    }
}
