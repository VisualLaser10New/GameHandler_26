package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.InvalidTournamentException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.DeleteTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.ScheduleTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.UpdateTournamentUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.TournamentDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.UpdateTournamentRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * <p>Implemented in FASE 5: {@code POST /{id}/schedule},
 * {@code GET /{id}/standings}, {@code GET /{id}/matches} (locked decision C.14).
 * Schedule generation ({@link ScheduleTournamentMatchesUseCase}) enforces the
 * {@code OPEN_REGISTRATION -> IN_PROGRESS} transition, single-elimination
 * pairing with byes and atomic outbox emission per SCHEDULED match.
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
    private final ScheduleTournamentMatchesUseCase scheduleUseCase;
    private final GetTournamentStandingsUseCase standingsUseCase;
    private final ListTournamentMatchesUseCase matchesUseCase;
    private final UpdateTournamentUseCase updateUseCase;
    private final DeleteTournamentUseCase deleteUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    public TournamentController(CreateTournamentUseCase createUseCase,
                                OpenTournamentRegistrationUseCase openUseCase,
                                CancelTournamentUseCase cancelUseCase,
                                GetTournamentUseCase getUseCase,
                                ListTournamentsUseCase listTournamentsUseCase,
                                CurrentUserService currentUserService,
                                Clock clock,
                                ScheduleTournamentMatchesUseCase scheduleUseCase,
                                GetTournamentStandingsUseCase standingsUseCase,
                                ListTournamentMatchesUseCase matchesUseCase,
                                UpdateTournamentUseCase updateUseCase,
                                DeleteTournamentUseCase deleteUseCase) {
        this.createUseCase = createUseCase;
        this.openUseCase = openUseCase;
        this.cancelUseCase = cancelUseCase;
        this.getUseCase = getUseCase;
        this.listTournamentsUseCase = listTournamentsUseCase;
        this.currentUserService = currentUserService;
        this.clock = clock;
        this.scheduleUseCase = scheduleUseCase;
        this.standingsUseCase = standingsUseCase;
        this.matchesUseCase = matchesUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    TournamentController(CreateTournamentUseCase createUseCase,
                         OpenTournamentRegistrationUseCase openUseCase,
                         CancelTournamentUseCase cancelUseCase,
                         GetTournamentUseCase getUseCase,
                         ListTournamentsUseCase listTournamentsUseCase,
                         CurrentUserService currentUserService,
                         Clock clock,
                         ScheduleTournamentMatchesUseCase scheduleUseCase,
                         GetTournamentStandingsUseCase standingsUseCase,
                         ListTournamentMatchesUseCase matchesUseCase) {
        this(createUseCase, openUseCase, cancelUseCase, getUseCase, listTournamentsUseCase,
                currentUserService, clock, scheduleUseCase, standingsUseCase, matchesUseCase, null, null);
    }

    /**
     * Crea un nuovo torneo a partire dai dati forniti.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN}. Il creatore del torneo
     * corrisponde all'utente autenticato e il torneo viene creato nello stato {@code DRAFT}
     * con formato a eliminazione singola.</p>
     *
     * @param request dto di richiesta con i dati del torneo, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentDto} del torneo creato
     * @throws InvalidTournamentException se l'utente autenticato non è risolvibile (mappato a {@code 400})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
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

    /**
     * Apre le iscrizioni per il torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN} e transita lo stato del
     * torneo in {@code OPEN_REGISTRATION}.</p>
     *
     * @param id identificativo del torneo da aprire, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentDto} aggiornato
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws com.gameplatform.central.domain.exception.InvalidTournamentStateException se lo stato attuale non consente l'apertura (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> open(@PathVariable String id) {
        return ResponseEntity.ok(openUseCase.open(new TournamentId(id)));
    }

    /**
     * Annulla il torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN} e transita lo stato del
     * torneo in {@code CANCELLED}.</p>
     *
     * @param id identificativo del torneo da annullare, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentDto} aggiornato
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws com.gameplatform.central.domain.exception.InvalidTournamentStateException se lo stato attuale non consente l'annullamento (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> cancel(@PathVariable String id) {
        return ResponseEntity.ok(cancelUseCase.cancel(new TournamentId(id)));
    }

    /**
     * Aggiorna i dati del torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN}.</p>
     *
     * @param id      identificativo del torneo da aggiornare, non {@code null} né vuoto
     * @param request dto di richiesta con i nuovi dati del torneo, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentDto} aggiornato
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws com.gameplatform.central.domain.exception.InvalidTournamentStateException se lo stato attuale non consente l'aggiornamento (mappato a {@code 400})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TournamentDto> update(@PathVariable String id,
                                                @Valid @RequestBody UpdateTournamentRequestDto request) {
        TournamentDto dto = updateUseCase.update(new TournamentId(id), request.name(), request.startsAt(),
                                                 request.buildingIds(), null);
        return ResponseEntity.ok(dto);
    }

    /**
     * Elimina il torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN}.</p>
     *
     * @param id identificativo del torneo da eliminare, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 204 No Content} e corpo vuoto
     * @throws com.gameplatform.central.domain.exception.TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws com.gameplatform.central.domain.exception.InvalidTournamentStateException se lo stato attuale non consente l'eliminazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        deleteUseCase.delete(new TournamentId(id), null);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restituisce l'elenco dei tornei, eventualmente filtrato per stato.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato. Se il filtro
     * {@code status} non è fornito restituisce tutti i tornei.</p>
     *
     * @param status stato opzionale per filtrare i tornei; se {@code null} o vuoto restituisce tutti i tornei
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link TournamentDto};
     *         la lista è vuota se nessun torneo soddisfa il filtro
     * @throws IllegalArgumentException se {@code status} non corrisponde a un valore valido di {@link TournamentStatus}
     */
    @GetMapping
    public ResponseEntity<List<TournamentDto>> list(@RequestParam(value = "status", required = false) String status) {
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(listTournamentsUseCase.findAll());
        }
        TournamentStatus parsedStatus = TournamentStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(listTournamentsUseCase.findByStatus(parsedStatus));
    }

    /**
     * Restituisce il torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato.</p>
     *
     * @param id identificativo del torneo da recuperare, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link TournamentDto} corrispondente
     * @throws TournamentNotFoundException se il torneo con l'identificativo indicato non esiste (mappato a {@code 404})
     * @see GlobalExceptionHandler
     */
    @GetMapping("/{id}")
    public ResponseEntity<TournamentDto> getById(@PathVariable String id) {
        return getUseCase.getById(new TournamentId(id))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
    }

    /**
     * Genera il calendario degli incontri per il torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione richiede il ruolo {@code PLATFORM_ADMIN}. Transita lo stato del torneo
     * da {@code OPEN_REGISTRATION} a {@code IN_PROGRESS} applicando l'accoppiamento a
     * eliminazione singola (con bye se necessari) e genera gli incontri.</p>
     *
     * @param id identificativo del torneo da programmare, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link TournamentMatchDto} generati
     * @throws TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @throws InvalidTournamentStateException se lo stato attuale non consente la programmazione (mappato a {@code 400})
     * @throws com.gameplatform.central.domain.exception.TournamentRegistrationClosedException se l'iscrizione non è aperta (mappato a {@code 409})
     * @see GlobalExceptionHandler
     */
    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<TournamentMatchDto>> schedule(@PathVariable String id) {
        return ResponseEntity.ok(scheduleUseCase.schedule(new TournamentId(id)));
    }

    /**
     * Restituisce la classifica del torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato.</p>
     *
     * @param id identificativo del torneo di cui leggere la classifica, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link TournamentStandingDto};
     *         la lista è vuota se non vi sono ancora posizioni in classifica
     * @throws TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @see GlobalExceptionHandler
     */
    @GetMapping("/{id}/standings")
    public ResponseEntity<List<TournamentStandingDto>> standings(@PathVariable String id) {
        return ResponseEntity.ok(standingsUseCase.getStandings(new TournamentId(id)));
    }

    /**
     * Restituisce gli incontri del torneo identificato dall'identificativo fornito.
     *
     * <p>L'operazione è disponibile a qualsiasi principal autenticato.</p>
     *
     * @param id identificativo del torneo di cui leggere gli incontri, non {@code null} né vuoto
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link TournamentMatchDto};
     *         la lista è vuota se non vi sono ancora incontri programmati
     * @throws TournamentNotFoundException se il torneo non esiste (mappato a {@code 404})
     * @see GlobalExceptionHandler
     */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<TournamentMatchDto>> matches(@PathVariable String id) {
        return ResponseEntity.ok(matchesUseCase.findByTournament(new TournamentId(id)));
    }
}
