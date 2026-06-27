package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionRecoveryHelperTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;

    Clock clock = Clock.fixed(Instant.parse("2026-06-27T12:00:00Z"), ZoneId.of("UTC"));
    ObjectMapper objectMapper = new ObjectMapper();

    SessionRecoveryHelper helper;

    @BeforeEach
    void setUp() {
        helper = new SessionRecoveryHelper(
                gameSessionRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                clock,
                objectMapper
        );
    }

    private GameSession createSession(GameId gameId, GameStatus status) {
        return new GameSession(
                new GameSessionId("s-1"),
                gameId,
                GameType.CHESS,
                new BuildingId("b-1"),
                status,
                Instant.parse("2026-06-27T11:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new UserId("u-1"))
        );
    }

    @Test
    void abortSessionShouldAbortSessionReleaseGameAndSaveOutboxEvent() throws Exception {
        GameId gameId = new GameId("g-1");
        GameSession session = createSession(gameId, GameStatus.IN_PROGRESS);
        Game game = new Game(gameId, GameType.CHESS, "Chess Table", new BuildingId("b-1"), GameMachineStatus.IN_USE);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        helper.abortSession(session);

        // Verify session was aborted and saved
        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertNotNull(session.getEndedAt());
        verify(gameSessionRepository).save(session);

        // Verify game was released and saved
        assertEquals(GameMachineStatus.AVAILABLE, game.getStatus());
        verify(gameRepository).save(game);
        verify(publishGameStatePort).publishState(gameId, GameMachineStatus.AVAILABLE);

        // Verify outbox event was generated and saved
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent event = outboxCaptor.getValue();
        assertEquals("GAME_SESSION_COMPLETED", event.getEventType());
        assertEquals("PENDING", event.getStatus());
        assertTrue(event.getPayload().contains("SERVER_RESTART"));
        assertTrue(event.getPayload().contains("s-1"));
    }

    @Test
    void abortSessionShouldWorkEvenIfGameNotFound() throws Exception {
        GameId gameId = new GameId("g-1");
        GameSession session = createSession(gameId, GameStatus.IN_PROGRESS);

        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        helper.abortSession(session);

        // Verify session was aborted and saved
        assertEquals(GameStatus.ABORTED, session.getStatus());
        verify(gameSessionRepository).save(session);

        // Verify no game interaction and no publish
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());

        // Verify outbox event saved
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}
