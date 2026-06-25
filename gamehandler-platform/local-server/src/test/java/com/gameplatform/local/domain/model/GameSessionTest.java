package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    @Test
    void shouldCreateSessionSuccessfully() {
        GameSessionId id = new GameSessionId("session-1");
        GameId gameId = new GameId("game-1");
        BuildingId buildingId = new BuildingId("building-1");
        Instant startedAt = Instant.parse("2026-06-25T10:00:00Z");
        List<UserId> participants = List.of(new UserId("user-1"), new UserId("user-2"));

        GameSession session = new GameSession(
            id, gameId, GameType.CHESS, buildingId, GameStatus.IN_PROGRESS, startedAt,
            null, null, null, null, null, participants
        );

        assertEquals(id, session.getId());
        assertEquals(gameId, session.getGameId());
        assertEquals(GameType.CHESS, session.getGameType());
        assertEquals(buildingId, session.getBuildingId());
        assertEquals(GameStatus.IN_PROGRESS, session.getStatus());
        assertEquals(startedAt, session.getStartedAt());
        assertNull(session.getEndedAt());
        assertNull(session.getDurationSeconds());
        assertEquals(participants, session.getParticipants());
    }

    @Test
    void shouldPauseAndResumeSession() {
        GameSession session = createSampleSession();

        session.pause();
        assertEquals(GameStatus.PAUSED, session.getStatus());

        session.resume();
        assertEquals(GameStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void shouldFailPausingIfNotInProgress() {
        GameSession session = createSampleSession();
        session.pause();
        assertThrows(IllegalStateException.class, session::pause);
    }

    @Test
    void shouldFailResumingIfNotPaused() {
        GameSession session = createSampleSession();
        assertThrows(IllegalStateException.class, session::resume);
    }

    @Test
    void shouldCompleteSessionWithWinnerResult() {
        GameSession session = createSampleSession();
        Instant endedAt = Instant.parse("2026-06-25T10:30:00Z"); // 30 minutes later (1800 seconds)

        UserId winner = new UserId("user-1");
        GameResult fakeResult = new GameResult() {
            @Override
            public UserId getWinnerId() {
                return winner;
            }

            @Override
            public List<UserId> getWinnerIds() {
                return List.of(winner);
            }

            @Override
            public WinCondition getWinCondition() {
                return WinCondition.WIN;
            }
        };

        session.complete(fakeResult, endedAt);

        assertEquals(GameStatus.COMPLETED, session.getStatus());
        assertEquals(endedAt, session.getEndedAt());
        assertEquals(1800, session.getDurationSeconds());
        assertEquals(winner, session.getWinnerId());
        assertEquals(WinCondition.WIN, session.getWinCondition());
        assertEquals(fakeResult, session.getResult());
    }

    @Test
    void shouldAbortSessionDueToTimeout() {
        GameSession session = createSampleSession();
        Instant endedAt = Instant.parse("2026-06-25T10:15:00Z"); // 15 minutes later (900 seconds)

        session.abort(StopReason.TIMEOUT, endedAt);

        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertEquals(endedAt, session.getEndedAt());
        assertEquals(900, session.getDurationSeconds());
        assertEquals(WinCondition.TIMEOUT, session.getWinCondition());
        assertNull(session.getWinnerId());
    }

    @Test
    void shouldAbortSessionDueToAbandonment() {
        GameSession session = createSampleSession();
        Instant endedAt = Instant.parse("2026-06-25T10:15:00Z");

        session.abort(StopReason.ABORTED, endedAt);

        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertEquals(endedAt, session.getEndedAt());
        assertEquals(900, session.getDurationSeconds());
        assertEquals(WinCondition.ABANDONED, session.getWinCondition());
        assertNull(session.getWinnerId());
    }

    private GameSession createSampleSession() {
        return new GameSession(
            new GameSessionId("session-1"),
            new GameId("game-1"),
            GameType.CHESS,
            new BuildingId("building-1"),
            GameStatus.IN_PROGRESS,
            Instant.parse("2026-06-25T10:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            List.of(new UserId("user-1"), new UserId("user-2"))
        );
    }
}
