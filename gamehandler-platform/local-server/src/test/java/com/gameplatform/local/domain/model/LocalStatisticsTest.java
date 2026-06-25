package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalStatisticsTest {

    @Test
    void shouldCreateLocalStatisticsSuccessfully() {
        Map<String, Double> winRates = Map.of("user-1", 0.75);
        LocalStatistics stats = new LocalStatistics(GameType.CHESS, 10, 1500.0, 5, winRates);

        assertEquals(GameType.CHESS, stats.getGameType());
        assertEquals(10, stats.getTotalSessions());
        assertEquals(1500.0, stats.getAvgDuration());
        assertEquals(5, stats.getTotalReservations());
        assertEquals(winRates, stats.getWinRateByUser());
    }

    @Test
    void shouldRecalculateStatisticsCorrectly() {
        // Prepare participants
        UserId user1 = new UserId("user-1");
        UserId user2 = new UserId("user-2");
        UserId user3 = new UserId("user-3");
        List<UserId> chessPlayers = List.of(user1, user2);
        List<UserId> dartsPlayers = List.of(user1, user3);

        BuildingId buildingId = new BuildingId("building-1");
        GameId gameId = new GameId("game-1");

        // GameResult for user-1 winning
        GameResult user1Wins = new GameResult() {
            @Override public UserId getWinnerId() { return user1; }
            @Override public List<UserId> getWinnerIds() { return List.of(user1); }
            @Override public WinCondition getWinCondition() { return WinCondition.WIN; }
        };

        // GameResult for draw
        GameResult drawResult = new GameResult() {
            @Override public UserId getWinnerId() { return null; }
            @Override public List<UserId> getWinnerIds() { return List.of(); }
            @Override public WinCondition getWinCondition() { return WinCondition.DRAW; }
        };

        // Create mock sessions
        // Chess Session 1: COMPLETED, user-1 wins, duration 1000s
        GameSession chess1 = new GameSession(
            new GameSessionId("s-1"), gameId, GameType.CHESS, buildingId, GameStatus.IN_PROGRESS,
            Instant.parse("2026-06-25T10:00:00Z"), null, null, null, null, null, chessPlayers
        );
        chess1.complete(user1Wins, Instant.parse("2026-06-25T10:16:40Z")); // 1000 seconds

        // Chess Session 2: COMPLETED, Draw, duration 2000s
        GameSession chess2 = new GameSession(
            new GameSessionId("s-2"), gameId, GameType.CHESS, buildingId, GameStatus.IN_PROGRESS,
            Instant.parse("2026-06-25T10:00:00Z"), null, null, null, null, null, chessPlayers
        );
        chess2.complete(drawResult, Instant.parse("2026-06-25T10:33:20Z")); // 2000 seconds

        // Chess Session 3: ABORTED, duration doesn't count towards avg completed duration, counts for total sessions
        GameSession chess3 = new GameSession(
            new GameSessionId("s-3"), gameId, GameType.CHESS, buildingId, GameStatus.ABORTED,
            Instant.parse("2026-06-25T10:00:00Z"), Instant.parse("2026-06-25T10:05:00Z"), 300, null, WinCondition.ABANDONED, null, chessPlayers
        );

        // Darts Session: COMPLETED, should be filtered out because it's not CHESS
        GameSession darts1 = new GameSession(
            new GameSessionId("s-4"), gameId, GameType.DARTS, buildingId, GameStatus.IN_PROGRESS,
            Instant.parse("2026-06-25T10:00:00Z"), null, null, null, null, null, dartsPlayers
        );
        darts1.complete(user1Wins, Instant.parse("2026-06-25T10:10:00Z"));

        // Instantiate LocalStatistics and recalculate
        LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
        stats.recalculate(List.of(chess1, chess2, chess3, darts1));

        // Assertions:
        // Total Chess sessions = 3 (chess1, chess2, chess3)
        assertEquals(3, stats.getTotalSessions());

        // Avg duration of completed Chess sessions = (1000 + 2000) / 2 = 1500.0
        assertEquals(1500.0, stats.getAvgDuration());

        // Win rates calculation:
        // user-1 participated in 3 chess sessions (chess1, chess2, chess3). Won 1 (chess1). Rate = 1/3 = ~0.3333
        // user-2 participated in 3 chess sessions (chess1, chess2, chess3). Won 0. Rate = 0/3 = 0.0
        // user-3 participated only in darts, so not in chess. Rate = should not be in map.
        Map<String, Double> winRates = stats.getWinRateByUser();
        assertEquals(2, winRates.size());
        assertTrue(winRates.containsKey("user-1"));
        assertTrue(winRates.containsKey("user-2"));
        assertFalse(winRates.containsKey("user-3"));

        assertEquals(1.0 / 3.0, winRates.get("user-1"), 0.0001);
        assertEquals(0.0, winRates.get("user-2"), 0.0001);
    }
}
