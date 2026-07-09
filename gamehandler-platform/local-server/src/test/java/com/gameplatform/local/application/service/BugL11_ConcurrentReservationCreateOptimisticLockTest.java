package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Bug L-11: POF-5 optimistic locking on reservation creation.
 * {@code ReservationService.create(...)} reserves the game machine
 * ({@code game.reserve()} then {@code gameRepository.save(game)}). When a
 * concurrent request loses the optimistic lock on that save, the adapter throws
 * {@link ConcurrentStateException}; the service must propagate it unchanged and
 * must NOT leak a Spring exception type.
 */
@ExtendWith(MockitoExtension.class)
class BugL11_ConcurrentReservationCreateOptimisticLockTest {

    @Mock ReservationRepository reservationRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;

    private ReservationService service;

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final GameId GAME_ID = new GameId("game-1");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ReservationService(
                reservationRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                clock,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("BUG L-11: a concurrent reservation create that loses the optimistic lock propagates ConcurrentStateException (not a Spring type)")
    void concurrentCreatePropagatesConcurrentStateException() {
        Game availableGame = new Game(GAME_ID, GameType.CHESS, "Chess 1",
                new BuildingId("b-1"), GameMachineStatus.AVAILABLE);

        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(availableGame));
        // The game save (AVAILABLE -> RESERVED) loses the optimistic lock and the
        // adapter raises ConcurrentStateException; stub the port to model that.
        when(gameRepository.save(any()))
                .thenThrow(new ConcurrentStateException("Concurrent modification of game game-1"));

        assertThrows(ConcurrentStateException.class, () ->
                service.create(GAME_ID, new UserId("user-1"),
                        NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2))));
    }
}