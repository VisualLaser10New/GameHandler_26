package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.TournamentDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter exposing the FASE 4 tournament CRUD + lifecycle endpoints
 * (PIANO_UTENTI_TORNEI.md §3.6). POST/PUT lifecycle writes are
 * {@code PLATFORM_ADMIN}; GET are {@code authenticated} (default per
 * {@code SecurityConfig.anyRequest().authenticated()}).
 *
 * <p>Deferred to FASE 5: {@code POST /{id}/schedule}, {@code GET /{id}/standings},
 * {@code GET /{id}/matches} (locked decision C.14).
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final CreateTournamentUseCase createUseCase;
    private final OpenTournamentRegistrationUseCase openUseCase;
    private final CancelTournamentUseCase cancelUseCase;
    private final GetTournamentUseCase getUseCase;
    private final ListTournamentsUseCase listTournamentsUseCase;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public TournamentController(CreateTournamentUseCase createUseCase,
                                OpenTournamentRegistrationUseCase openUseCase,
                                CancelTournamentUseCase cancelUseCase,
                                GetTournamentUseCase getUseCase,
                                ListTournamentsUseCase listTournamentsUseCase,
                                CurrentUserService currentUserService,
                                Clock clock) {
        this.createUseCase = createUseCase;
        this.openUseCase = openUseCase;
        this.cancelUseCase = cancelUseCase;
        this.getUseCase = getUseCase;
        this.listTournamentsUseCase = listTournamentsUseCase;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> create(@Valid @RequestBody CreateTournamentRequestDto request) {
        UserId createdBy = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new InvalidTournamentException("Authenticated user could not be resolved"));
        Tournament tournament = new Tournament(
                new TournamentId(UUID.randomUUID().toString()),
                request.name(),
                request.gameType(),
                request.teamBased(),
                request.teamSize(),
                TournamentFormat.SINGLE_ELIMINATION,
                TournamentStatus.DRAFT,
                request.startsAt(),
                null,
                createdBy,
                Instant.now(clock));
        return ResponseEntity.ok(createUseCase.create(tournament, request.buildingIds()));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> open(@PathVariable String id) {
        return ResponseEntity.ok(openUseCase.open(new TournamentId(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> cancel(@PathVariable String id) {
        return ResponseEntity.ok(cancelUseCase.cancel(new TournamentId(id)));
    }

    @GetMapping
    public ResponseEntity<List<TournamentDto>> list(@RequestParam(value = "status", required = false) String status) {
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(listTournamentsUseCase.findAll());
        }
        TournamentStatus parsedStatus = TournamentStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(listTournamentsUseCase.findByStatus(parsedStatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentDto> getById(@PathVariable String id) {
        return getUseCase.getById(new TournamentId(id))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
    }
}
