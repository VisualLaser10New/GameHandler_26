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

@RestController
@RequestMapping("/api/reservations")
@PreAuthorize("hasRole('PLAYER')")
public class ReservationController {

    private final CreateReservationUseCase createReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final GetReservationsUseCase getReservationsUseCase;
    private final CurrentUserService currentUserService;

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

    @PostMapping
    public ResponseEntity<ReservationDto> create(@RequestBody CreateReservationRequestDto req) {
        Reservation reservation = createReservationUseCase.create(
                new GameId(req.gameId()),
                new UserId(req.userId()),
                req.startTime(),
                req.endTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(reservation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        cancelReservationUseCase.cancel(new ReservationId(id));
        return ResponseEntity.noContent().build();
    }

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
