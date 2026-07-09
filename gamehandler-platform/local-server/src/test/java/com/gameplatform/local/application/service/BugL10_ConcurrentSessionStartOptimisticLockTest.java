package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Bug L-10: POF-5 optimistic locking. When two concurrent
 * {@code GameSessionService.start(...)} calls race on the same game machine,
 * the loser's {@code gameRepository.save(game)} throws
 * {@link ConcurrentStateException} (translated by
 * {@code GameRepositoryAdapter} from a Spring optimistic-lock failure). The
 * service must let the domain exception propagate untouched and must NOT leak
 * a Spring exception type into the application layer.
 */
@ExtendWith(MockitoExtension.class)
class BugL10_ConcurrentSessionStartOptimisticLockTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;

    private GameSessionService service;

    private static final Instant NOW = Instant.parse("2026-06-29T08:00:00Z");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("building-1");
    private static final UserId USER_ID = new UserId("user-1");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new GameSessionService(
                gameSessionRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                reservationRepository,
                clock,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("BUG L-10: a concurrent session start that loses the optimistic lock propagates ConcurrentStateException (not a Spring type)")
    void concurrentStartPropagatesConcurrentStateException() {
        Game availableGame = new Game(GAME_ID, GameType.CHESS, "Chess Table",
                BUILDING_ID, GameMachineStatus.AVAILABLE);

        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(availableGame));
        // The adapter would translate a Hibernate optimistic-lock failure into
        // ConcurrentStateException; stub the repository port to model that.
        when(gameRepository.save(any()))
                .thenThrow(new ConcurrentStateException("Concurrent modification of game game-1"));

        assertThrows(ConcurrentStateException.class, () -> service.start(
                GAME_ID,
                GameType.CHESS,
                List.of(USER_ID, new UserId("opponent")),
                null
        ));
    }
}