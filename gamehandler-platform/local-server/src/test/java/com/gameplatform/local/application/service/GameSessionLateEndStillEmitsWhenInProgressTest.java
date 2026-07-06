package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S1 happy-path counterpart: a session that is IN_PROGRESS and is then ended
 * emits EXACTLY ONE {@code GAME_SESSION_COMPLETED} outbox event (the count
 * invariant on the producer side — exactly-once emission per natural session end).
 *
 * <p>This pins the inverse of the S1 guard: the {@code if (!wasAborted)} wrapper
 * added to {@code GameSessionService.end()} must not suppress emission on the
 * normal completion path.
 */
@ExtendWith(MockitoExtension.class)
class GameSessionLateEndStillEmitsWhenInProgressTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock Clock clock;
    @Mock ObjectMapper objectMapper;
    @Mock GameResult result;

    @InjectMocks GameSessionService service;

    @BeforeEach
    void stubClockAndResult() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(result.getWinnerId()).thenReturn(new UserId("winner"));
        lenient().when(result.getWinCondition()).thenReturn(WinCondition.WIN);
    }

    @Test
    @DisplayName("end() on an IN_PROGRESS session emits exactly one GAME_SESSION_COMPLETED outbox event")
    void endOnInProgressSessionEmitsExactlyOneCompletedOutboxEvent() throws Exception {
        GameSession s = new GameSession(
                new GameSessionId("s-1"), new GameId("game-1"), GameType.CHESS, new BuildingId("b-1"),
                GameStatus.IN_PROGRESS, NOW, null, null, null, null, null, List.of(new UserId("u-1")));
        Game game = new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"),
                GameMachineStatus.IN_USE);

        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(game));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.end(new GameSessionId("s-1"), result);

        assertEquals(GameStatus.COMPLETED, s.getStatus());
        verify(gameSessionRepository).save(s);
        verify(gameRepository).save(any());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("GAME_SESSION_COMPLETED", saved.getEventType());
    }
}