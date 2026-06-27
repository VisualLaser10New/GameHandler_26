package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocalStatisticsTest {

    private static final BuildingId BUILDING = new BuildingId("building-1");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final Instant STARTED_AT = Instant.parse("2026-06-25T10:00:00Z");

    private static GameResult resultWith(UserId winner, WinCondition condition) {
        return new GameResult() {
            @Override
            public UserId getWinnerId() {
                return winner;
            }

            @Override
            public List<UserId> getWinnerIds() {
                return winner == null ? List.of() : List.of(winner);
            }

            @Override
            public WinCondition getWinCondition() {
                return condition;
            }
        };
    }

    private static GameSession session(GameType type, GameStatus status, Instant startedAt,
                                        Instant endedAt, Integer duration, UserId winner,
                                        WinCondition wc, List<UserId> participants) {
        return new GameSession(new GameSessionId("s-" + type + "-" + status + "-" + System.nanoTime()), GAME_ID, type,
                BUILDING, status, startedAt, endedAt, duration, winner, wc, null, participants);
    }

    @Nested
    class Construction {

        @Test
        void shouldCreateLocalStatisticsSuccessfully() {
            Map<String, Double> winRates = Map.of("user-1", 0.75);

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 10, 1500.0, 5, winRates);

            assertThat(stats.getGameType()).isEqualTo(GameType.CHESS);
            assertThat(stats.getTotalSessions()).isEqualTo(10);
            assertThat(stats.getAvgDuration()).isEqualTo(1500.0);
            assertThat(stats.getTotalReservations()).isEqualTo(5);
            assertThat(stats.getWinRateByUser()).containsEntry("user-1", 0.75);
        }

        @Test
        void shouldRejectNullGameType() {
            assertThatThrownBy(() -> new LocalStatistics(null, 0, 0.0, 0, new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldDefaultToEmptyMapWhenWinRateByUserIsNull() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, null);
            assertThat(stats.getWinRateByUser()).isNotNull().isEmpty();
        }

        @Test
        void shouldReturnAnUnmodifiableMapWhenProvidedAtConstruction() {
            Map<String, Double> rates = new HashMap<>();
            rates.put("user-1", 0.5);
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, rates);

            assertThatThrownBy(() -> stats.getWinRateByUser().put("user-2", 0.1))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void setTotalReservationsShouldReplaceValue() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 5, new HashMap<>());
            stats.setTotalReservations(42);
            assertThat(stats.getTotalReservations()).isEqualTo(42);
        }

        @Test
        void setTotalReservationsAcceptsNegativeValue() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 5, new HashMap<>());
            stats.setTotalReservations(-1);
            assertThat(stats.getTotalReservations()).isEqualTo(-1);
        }

        @Test
        void shouldAcceptNegativeTotalSessionsAndAvgDurationAtConstruction() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, -3, -10.0, -2, new HashMap<>());
            assertThat(stats.getTotalSessions()).isEqualTo(-3);
            assertThat(stats.getAvgDuration()).isEqualTo(-10.0);
            assertThat(stats.getTotalReservations()).isEqualTo(-2);
        }

        @Test
        void shouldRejectMapWithNullKeyViaCopyOf() {
            Map<String, Double> rates = new HashMap<>();
            rates.put(null, 0.5);
            assertThatThrownBy(() -> new LocalStatistics(GameType.CHESS, 0, 0.0, 0, rates))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectMapWithNullValueViaCopyOf() {
            Map<String, Double> rates = new HashMap<>();
            rates.put("user-1", null);
            assertThatThrownBy(() -> new LocalStatistics(GameType.CHESS, 0, 0.0, 0, rates))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Recalculate {

        @Test
        void shouldRejectNullSessionsList() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            assertThatThrownBy(() -> stats.recalculate(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldResetAllCountersWhenNoSessionsOfTheGivenType() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 99, 999.0, 7, Map.of("u", 1.0));
            stats.recalculate(List.of());

            assertThat(stats.getTotalSessions()).isZero();
            assertThat(stats.getAvgDuration()).isZero();
            assertThat(stats.getWinRateByUser()).isEmpty();
            assertThat(stats.getTotalReservations()).isEqualTo(7);
        }

        @Test
        void shouldFilterOutSessionsOfOtherGameTypes() {
            GameResult user1Wins = resultWith(new UserId("user-1"), WinCondition.WIN);
            List<UserId> chessPlayers = List.of(new UserId("user-1"), new UserId("user-2"));
            List<UserId> dartsPlayers = List.of(new UserId("user-1"), new UserId("user-3"));

            GameSession chessCompleted = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, chessPlayers);
            chessCompleted.complete(user1Wins, STARTED_AT.plusSeconds(1000));

            GameSession dartsCompleted = session(GameType.DARTS, GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, dartsPlayers);
            dartsCompleted.complete(user1Wins, STARTED_AT.plusSeconds(500));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(chessCompleted, dartsCompleted));

            assertThat(stats.getTotalSessions()).isEqualTo(1);
            assertThat(stats.getAvgDuration()).isEqualTo(1000.0);
            assertThat(stats.getWinRateByUser()).containsOnlyKeys("user-1", "user-2");
        }

        @Test
        void shouldComputeAvgDurationOnlyFromCompletedSessionsWithNonNullDuration() {
            UserId u1 = new UserId("user-1");
            UserId u2 = new UserId("user-2");
            List<UserId> players = List.of(u1, u2);
            GameResult user1Wins = resultWith(u1, WinCondition.WIN);
            GameResult draw = resultWith(null, WinCondition.DRAW);

            GameSession s1 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s1.complete(user1Wins, STARTED_AT.plusSeconds(1000));

            GameSession s2 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s2.complete(draw, STARTED_AT.plusSeconds(2000));

            GameSession s3 = session(GameType.CHESS, GameStatus.ABORTED, STARTED_AT,
                    STARTED_AT.plusSeconds(300), 300, null, WinCondition.ABANDONED, players);

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s1, s2, s3));

            assertThat(stats.getTotalSessions()).isEqualTo(3);
            assertThat(stats.getAvgDuration()).isEqualTo(1500.0);
        }

        @Test
        void shouldComputeWinRateAsWinsOverParticipations() {
            UserId u1 = new UserId("user-1");
            UserId u2 = new UserId("user-2");
            List<UserId> players = List.of(u1, u2);
            GameResult user1Wins = resultWith(u1, WinCondition.WIN);
            GameResult draw = resultWith(null, WinCondition.DRAW);

            GameSession s1 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s1.complete(user1Wins, STARTED_AT.plusSeconds(1000));

            GameSession s2 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s2.complete(draw, STARTED_AT.plusSeconds(2000));

            GameSession s3 = session(GameType.CHESS, GameStatus.ABORTED, STARTED_AT,
                    STARTED_AT.plusSeconds(300), 300, null, WinCondition.ABANDONED, players);

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s1, s2, s3));

            Map<String, Double> rates = stats.getWinRateByUser();
            assertThat(rates).containsOnlyKeys("user-1", "user-2");
            assertThat(rates.get("user-1")).isCloseTo(1.0 / 3.0, within(0.0001));
            assertThat(rates.get("user-2")).isCloseTo(0.0, within(0.0001));
        }

        @Test
        void shouldNotCountUserThatNeverParticipatedInAnyMatchingSession() {
            UserId u1 = new UserId("user-1");
            UserId u2 = new UserId("user-2");
            UserId u3 = new UserId("user-3");
            List<UserId> chessPlayers = List.of(u1, u2);
            List<UserId> dartsPlayers = List.of(u1, u3);
            GameResult user1Wins = resultWith(u1, WinCondition.WIN);

            GameSession chess1 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null,
                    null, null, null, chessPlayers);
            chess1.complete(user1Wins, STARTED_AT.plusSeconds(1000));

            GameSession darts1 = session(GameType.DARTS, GameStatus.IN_PROGRESS, STARTED_AT, null,
                    null, null, null, dartsPlayers);
            darts1.complete(user1Wins, STARTED_AT.plusSeconds(500));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(chess1, darts1));

            assertThat(stats.getWinRateByUser()).doesNotContainKey("user-3");
        }

        @Test
        void shouldTreatSessionsWithNullParticipantsDefensively() {
            GameSession nullParticipantsSession = session(GameType.CHESS, GameStatus.IN_PROGRESS,
                    STARTED_AT, null, null, null, null, null);
            nullParticipantsSession.complete(resultWith(null, WinCondition.DRAW),
                    STARTED_AT.plusSeconds(60));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(nullParticipantsSession));

            assertThat(stats.getTotalSessions()).isEqualTo(1);
            assertThat(stats.getAvgDuration()).isEqualTo(60.0);
            assertThat(stats.getWinRateByUser()).isEmpty();
        }

        @Test
        void shouldNotResetTotalReservationsDuringRecalculate() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 12, new HashMap<>());
            stats.recalculate(List.of());
            assertThat(stats.getTotalReservations()).isEqualTo(12);
        }

        @Test
        void winRateByUserMapAfterRecalculateIsMutableHashMap() {
            GameSession s = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.plusSeconds(10), 10, null, null,
                    List.of(new UserId("user-1")));
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s));

            assertThat(stats.getWinRateByUser()).isInstanceOf(HashMap.class);
            stats.getWinRateByUser().put("user-2", 0.5);
            assertThat(stats.getWinRateByUser()).containsKey("user-2");
        }

        @Test
        void shouldCountAllParticipationsAcrossMultipleSessions() {
            UserId u1 = new UserId("user-1");
            UserId u2 = new UserId("user-2");
            List<UserId> players = List.of(u1, u2);

            GameSession s1 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s1.complete(resultWith(u1, WinCondition.WIN), STARTED_AT.plusSeconds(100));

            GameSession s2 = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s2.complete(resultWith(u2, WinCondition.WIN), STARTED_AT.plusSeconds(200));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s1, s2));

            assertThat(stats.getWinRateByUser().get("user-1")).isCloseTo(0.5, within(0.0001));
            assertThat(stats.getWinRateByUser().get("user-2")).isCloseTo(0.5, within(0.0001));
        }

        @Test
        void shouldIgnoreWinnerNotPresentInParticipants() {
            UserId u1 = new UserId("user-1");
            UserId u2 = new UserId("user-2");
            UserId outsider = new UserId("user-3");
            List<UserId> players = List.of(u1, u2);

            GameSession s = session(GameType.CHESS, GameStatus.IN_PROGRESS, STARTED_AT, null, null,
                    null, null, players);
            s.complete(resultWith(outsider, WinCondition.WIN), STARTED_AT.plusSeconds(100));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s));

            assertThat(stats.getWinRateByUser()).doesNotContainKey("user-3");
            assertThat(stats.getWinRateByUser().get("user-1")).isCloseTo(0.0, within(0.0001));
            assertThat(stats.getWinRateByUser().get("user-2")).isCloseTo(0.0, within(0.0001));
        }

        @Test
        void shouldCountSessionWithoutParticipantsAsASessionButNoUsers() {
            GameSession s = session(GameType.CHESS, GameStatus.COMPLETED, STARTED_AT,
                    STARTED_AT.plusSeconds(30), 30, null, null, List.of());

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s));

            assertThat(stats.getTotalSessions()).isEqualTo(1);
            assertThat(stats.getAvgDuration()).isEqualTo(30.0);
            assertThat(stats.getWinRateByUser()).isEmpty();
        }

        @Test
        void shouldReturnZeroAvgDurationWhenAllCompletedSessionsLackDuration() {
            GameSession s = session(GameType.CHESS, GameStatus.COMPLETED, STARTED_AT, null, null,
                    null, null, List.of(new UserId("u")));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(s));

            assertThat(stats.getTotalSessions()).isEqualTo(1);
            assertThat(stats.getAvgDuration()).isZero();
        }

        @Test
        void shouldCountAbortedSessionInTotalSessionsButNotInAvgDuration() {
            GameSession aborted = session(GameType.CHESS, GameStatus.ABORTED, STARTED_AT,
                    STARTED_AT.plusSeconds(500), 500, null, WinCondition.ABANDONED,
                    List.of(new UserId("u")));

            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0, new HashMap<>());
            stats.recalculate(List.of(aborted));

            assertThat(stats.getTotalSessions()).isEqualTo(1);
            assertThat(stats.getAvgDuration()).isZero();
        }

        @Test
        void recalculateReplacesPreviouslyComputedRates() {
            LocalStatistics stats = new LocalStatistics(GameType.CHESS, 0, 0.0, 0,
                    new HashMap<>(Map.of("stale", 1.0)));

            stats.recalculate(List.of());

            assertThat(stats.getWinRateByUser()).doesNotContainKey("stale").isEmpty();
        }
    }

    @Nested
    class Equality {

        @Test
        void localStatisticsDoNotOverrideEqualsSoIdentityEqualityHolds() {
            LocalStatistics a = new LocalStatistics(GameType.CHESS, 1, 1.0, 1, new HashMap<>());
            LocalStatistics b = new LocalStatistics(GameType.CHESS, 1, 1.0, 1, new HashMap<>());
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
        }
    }
}
