package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            GameRepository gameRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 60000)
    public void expireReservations() {
        List<Reservation> expiredReservations = reservationRepository.findExpired(Instant.now(clock));

        for (Reservation reservation : expiredReservations) {
            reservation.expire();
            reservationRepository.save(reservation);

            Game game = gameRepository.findById(reservation.getGameId()).orElse(null);
            if (game != null) {
                game.release();
                gameRepository.save(game);
                publishGameStatePort.publishState(game.getId(), game.getStatus());
            }
        }
    }
}
