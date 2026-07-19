package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.in.CancelReservationUseCase;
import com.gameplatform.local.domain.ports.in.CreateReservationUseCase;
import com.gameplatform.local.domain.ports.in.GetReservationsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.CreateReservationRequestDto;
import com.gameplatform.shared.dto.ReservationDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller REST per la gestione delle prenotazioni delle macchine da gioco.
 * Espone endpoint per creare, cancellare e consultare prenotazioni.
 * Gli utenti PLAYER possono gestire solo le proprie prenotazioni; i
 * PLATFORM_ADMIN possono gestire qualsiasi prenotazione.
 *
 * @see CreateReservationUseCase
 * @see CancelReservationUseCase
 * @see GetReservationsUseCase
 */
@RestController
@RequestMapping("/api/reservations")
@PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")
public class ReservationController {

    private final CreateReservationUseCase createReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final GetReservationsUseCase getReservationsUseCase;
    private final CurrentUserService currentUserService;

    /**
     * Costruisce il controller con i casi d'uso per la gestione delle
     * prenotazioni e il servizio per l'utente corrente.
     *
     * @param createReservationUseCase caso d'uso per la creazione prenotazioni
     * @param cancelReservationUseCase caso d'uso per la cancellazione prenotazioni
     * @param getReservationsUseCase caso d'uso per la consultazione prenotazioni
     * @param currentUserService servizio per la risoluzione dell'utente autenticato
     */
    public ReservationController(
            CreateReservationUseCase createReservationUseCase,
            CancelReservationUseCase cancelReservationUseCase,
            GetReservationsUseCase getReservationsUseCase,
            CurrentUserService currentUserService) {
        this.createReservationUseCase = createReservationUseCase;
        this.cancelReservationUseCase = cancelReservationUseCase;
        this.getReservationsUseCase = getReservationsUseCase;
        this.currentUserService = currentUserService;
    }

    /**
     * Crea una nuova prenotazione per una macchina da gioco.
     *
     * @param req i dati della richiesta di prenotazione
     * @return una {@link ResponseEntity} con status 201 e il {@link ReservationDto}
     * @throws org.springframework.security.access.AccessDeniedException se si tenta di creare
     *         una prenotazione per un altro utente senza ruolo PLATFORM_ADMIN
     */
    @PostMapping
    public ResponseEntity<ReservationDto> create(@RequestBody CreateReservationRequestDto req) {
        Optional<UserId> me = currentUserService.getCurrentUserId();
        if (me.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserId bodyUserId = new UserId(req.userId());
        boolean isPlatformAdmin = currentUserService.hasRole("PLATFORM_ADMIN");
        if (!isPlatformAdmin && !me.get().equals(bodyUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot create a reservation for another user");
        }
        Reservation reservation = createReservationUseCase.create(
                new GameId(req.gameId()),
                bodyUserId,
                req.startTime(),
                req.endTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(reservation));
    }

    /**
     * Cancella una prenotazione esistente.
     *
     * @param id l'identificativo della prenotazione
     * @return una {@link ResponseEntity} con status 204 (nessun contenuto)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        Optional<UserId> me = currentUserService.getCurrentUserId();
        if (me.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ReservationId rid = new ReservationId(id);
        if (currentUserService.hasRole("PLATFORM_ADMIN")) {
            cancelReservationUseCase.cancel(rid);
        } else {
            cancelReservationUseCase.cancel(rid, me.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Restituisce le prenotazioni di un determinato utente. L'utente
     * autenticato può vedere solo le proprie prenotazioni.
     *
     * @param userId l'identificativo dell'utente
     * @return una {@link ResponseEntity} con la lista di {@link ReservationDto}
     * @throws org.springframework.security.access.AccessDeniedException se si tenta di
     *         visualizzare le prenotazioni di un altro utente
     */
    @GetMapping
    public ResponseEntity<List<ReservationDto>> getByUser(@RequestParam("userId") String userId) {
        Optional<UserId> me = currentUserService.getCurrentUserId();
        if (me.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!me.get().value().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot view reservations of another user");
        }
        List<Reservation> reservations = getReservationsUseCase.getByUser(new UserId(userId));
        List<ReservationDto> dtos = reservations.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Converte una {@link Reservation} nel corrispondente {@link ReservationDto}.
     *
     * @param reservation la prenotazione da convertire
     * @return il DTO corrispondente
     */
    private ReservationDto toDto(Reservation reservation) {
        return new ReservationDto(
                reservation.getId().value(),
                reservation.getGameId().id(),
                reservation.getUserId().value(),
                reservation.getStatus(),
                reservation.getStartTime(),
                reservation.getEndTime()
        );
    }
}
