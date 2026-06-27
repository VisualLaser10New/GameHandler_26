package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private static final List<UserId> PARTICIPANTS =
            List.of(new UserId("user-1"), new UserId("user-2"));

    private static GameSession sample() {
        return new GameSession(new GameSessionId("session-1"), new GameId("game-1"),
                GameType.CHESS, new BuildingId("building-1"), GameStatus.IN_PROGRESS,
                STARTED_AT, null, null, null, null, null, PARTICIPANTS);
    }

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

    @Nested
    class Construction {

        @Test
        void shouldCreateSessionWithParticipantsSuccessfully() {
            GameSessionId id = new GameSessionId("session-1");
            GameId gameId = new GameId("game-1");
            BuildingId buildingId = new BuildingId("building-1");

            GameSession session = new GameSession(id, gameId, GameType.CHESS, buildingId,
                    GameStatus.IN_PROGRESS, STARTED_AT, null, null, null, null, null, PARTICIPANTS);

            assertThat(session.getId()).isEqualTo(id);
            assertThat(session.getGameId()).isEqualTo(gameId);
            assertThat(session.getGameType()).isEqualTo(GameType.CHESS);
            assertThat(session.getBuildingId()).isEqualTo(buildingId);
            assertThat(session.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(session.getEndedAt()).isNull();
            assertThat(session.getDurationSeconds()).isNull();
            assertThat(session.getParticipants()).isEqualTo(PARTICIPANTS);
        }

        @Test
        void shouldDefaultParticipantsToEmptyListWhenUsingCompatConstructor() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, null);
            assertThat(session.getParticipants()).isEmpty();
        }

        @Test
        void shouldDefaultParticipantsToEmptyListWhenPassingNullParticipants() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, null, null);
            assertThat(session.getParticipants()).isNotNull().isEmpty();
        }

        @Test
        void nullParticipantsProducesImmutableList() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, null, null);
            assertThatThrownBy(() -> session.getParticipants().add(new UserId("x")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnAnUnmodifiableParticipantsListWhenProvided() {
            GameSession session = sample();
            assertThatThrownBy(() -> session.getParticipants().add(new UserId("x")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldRejectAnyNullRequiredField() {
            GameSessionId id = new GameSessionId("s");
            GameId gameId = new GameId("g");
            GameType type = GameType.CHESS;
            BuildingId building = new BuildingId("b");
            GameStatus status = GameStatus.IN_PROGRESS;

            assertThatThrownBy(() -> new GameSession(null, gameId, type, building, status,
                    STARTED_AT, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSession(id, null, type, building, status,
                    STARTED_AT, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSession(id, gameId, null, building, status,
                    STARTED_AT, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSession(id, gameId, type, null, status,
                    STARTED_AT, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSession(id, gameId, type, building, null,
                    STARTED_AT, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSession(id, gameId, type, building, status,
                    null, null, null, null, null, null, PARTICIPANTS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptAllGameStatusesAtConstruction() {
            for (GameStatus status : GameStatus.values()) {
                GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                        GameType.CHESS, new BuildingId("b"), status, STARTED_AT,
                        null, null, null, null, null, PARTICIPANTS);
                assertThat(session.getStatus()).isEqualTo(status);
            }
        }

        @Test
        void shouldPreserveParticipantsOrdering() {
            List<UserId> players = List.of(new UserId("a"), new UserId("b"), new UserId("c"));
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, null, players);
            assertThat(session.getParticipants()).containsExactlyElementsOf(players);
        }

        @Test
        void shouldNotBeAffectedByMutationsOfOriginalParticipantsList() {
            List<UserId> players = new ArrayList<>(List.of(new UserId("a")));
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, null, null, null, null, players);
            players.add(new UserId("b"));
            assertThat(session.getParticipants()).hasSize(1);
        }
    }

    @Nested
    class PauseAndResume {

        @Test
        void shouldPauseAndResumeSession() {
            GameSession session = sample();
            session.pause();
            assertThat(session.getStatus()).isEqualTo(GameStatus.PAUSED);
            session.resume();
            assertThat(session.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void shouldFailPausingIfNotInProgress() {
            GameSession session = sample();
            session.pause();
            assertThatThrownBy(session::pause).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailResumingIfNotPaused() {
            GameSession session = sample();
            assertThatThrownBy(session::resume).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailPausingAlreadyCompletedSession() {
            GameSession session = sample();
            session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(60));
            assertThatThrownBy(session::pause).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailPausingAbortedSession() {
            GameSession session = sample();
            session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(60));
            assertThatThrownBy(session::pause).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailResumingAbortedSession() {
            GameSession session = sample();
            session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(60));
            assertThatThrownBy(session::resume).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailResumingInProgressSession() {
            GameSession session = sample();
            assertThatThrownBy(session::resume).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailPausingWaitingSession() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.WAITING, STARTED_AT,
                    null, null, null, null, null, PARTICIPANTS);
            assertThatThrownBy(session::pause).isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void pauseResumeCycleIsRepeatable() {
            GameSession session = sample();
            session.pause();
            session.resume();
            session.pause();
            session.resume();
            assertThat(session.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        }
    }

    @Nested
    class Complete {

        @Test
        void shouldCompleteSessionWithWinnerResultAndComputeDuration() {
            GameSession session = sample();
            Instant endedAt = STARTED_AT.plusSeconds(1800);
            UserId winner = new UserId("user-1");
            GameResult result = resultWith(winner, WinCondition.WIN);

            session.complete(result, endedAt);

            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
            assertThat(session.getEndedAt()).isEqualTo(endedAt);
            assertThat(session.getDurationSeconds()).isEqualTo(1800);
            assertThat(session.getWinnerId()).isEqualTo(winner);
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.WIN);
            assertThat(session.getResult()).isEqualTo(result);
        }

        @Test
        void shouldCompleteSessionWithDrawResultLeavingWinnerNull() {
            GameSession session = sample();
            GameResult draw = resultWith(null, WinCondition.DRAW);

            session.complete(draw, STARTED_AT.plusSeconds(1000));

            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
            assertThat(session.getWinnerId()).isNull();
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.DRAW);
        }

        @Test
        void shouldCompleteWithNullResultWithoutSettingWinnerOrWinCondition() {
            GameSession session = sample();

            session.complete(null, STARTED_AT.plusSeconds(60));

            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
            assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(60));
            assertThat(session.getDurationSeconds()).isEqualTo(60);
            assertThat(session.getWinnerId()).isNull();
            assertThat(session.getWinCondition()).isNull();
            assertThat(session.getResult()).isNull();
        }

        @Test
        void shouldComputeZeroDurationWhenEndedAtEqualsStartedAt() {
            GameSession session = sample();
            session.complete(resultWith(null, WinCondition.DRAW), STARTED_AT);
            assertThat(session.getDurationSeconds()).isZero();
        }

        @Test
        void completeDoesNotGuardPreviousStateAndCanCompleteAnAbortedSession() {
            GameSession session = sample();
            session.abort(StopReason.TIMEOUT, STARTED_AT.plusSeconds(60));

            session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(120));

            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
            assertThat(session.getWinnerId()).isEqualTo(new UserId("user-1"));
        }

        @Test
        void completeWithoutExplicitEndedAtUsesInstantNow() {
            GameSession session = sample();
            Instant before = Instant.now();
            session.complete(resultWith(null, WinCondition.DRAW));
            Instant after = Instant.now();
            assertThat(session.getEndedAt()).isBetween(before, after);
        }

        @Test
        void completeCannotCompleteAnAlreadyCompletedSession() {
            GameSession session = sample();
            session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(60));
            assertThatThrownBy(() -> session.complete(resultWith(new UserId("user-2"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(120)))
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void completeCanCompleteAPausedSession() {
            GameSession session = sample();
            session.pause();
            session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(60));
            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
        }

        @Test
        void shouldFailCompletingWaitingSession() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.WAITING, STARTED_AT,
                    null, null, null, null, null, PARTICIPANTS);
            assertThatThrownBy(() -> session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(60)))
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }
    }

    @Nested
    class Abort {

        @Test
        void shouldAbortDueToTimeoutSettingTimeoutWinCondition() {
            GameSession session = sample();
            session.abort(StopReason.TIMEOUT, STARTED_AT.plusSeconds(900));
            assertThat(session.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(session.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(900));
            assertThat(session.getDurationSeconds()).isEqualTo(900);
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.TIMEOUT);
            assertThat(session.getWinnerId()).isNull();
        }

        @Test
        void shouldAbortDueToAbortedReasonSettingAbandonedWinCondition() {
            GameSession session = sample();
            session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(900));
            assertThat(session.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.ABANDONED);
        }

        @Test
        void shouldMapStopReasonCompletedToAbandonedWinCondition() {
            GameSession session = sample();
            session.abort(StopReason.COMPLETED, STARTED_AT.plusSeconds(60));
            assertThat(session.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.ABANDONED);
        }

        @Test
        void shouldNotThrowWhenAbortReasonIsNullAndDefaultsToAbandoned() {
            GameSession session = sample();
            session.abort(null, STARTED_AT.plusSeconds(60));
            assertThat(session.getStatus()).isEqualTo(GameStatus.ABORTED);
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.ABANDONED);
        }

        @Test
        void abortWithoutExplicitEndedAtUsesInstantNow() {
            GameSession session = sample();
            Instant before = Instant.now();
            session.abort(StopReason.ABORTED);
            Instant after = Instant.now();
            assertThat(session.getEndedAt()).isBetween(before, after);
        }

        @Test
        void abortCannotBeReinvokedOnAbortedSession() {
            GameSession session = sample();
            session.abort(StopReason.TIMEOUT, STARTED_AT.plusSeconds(60));
            assertThatThrownBy(() -> session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(120)))
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void abortCannotAbortACompletedSession() {
            GameSession session = sample();
            session.complete(resultWith(new UserId("user-1"), WinCondition.WIN),
                    STARTED_AT.plusSeconds(60));
            assertThatThrownBy(() -> session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(120)))
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldFailAbortingWaitingSession() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.WAITING, STARTED_AT,
                    null, null, null, null, null, PARTICIPANTS);
            assertThatThrownBy(() -> session.abort(StopReason.ABORTED, STARTED_AT.plusSeconds(60)))
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }
    }

    @Nested
    class DurationCalculation {

        @Test
        void calculateDurationShouldLeavePreviousValueWhenEndedAtIsNull() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    null, 42, null, null, null, PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isEqualTo(42);
        }

        @Test
        void calculateDurationShouldComputeWhenBothStartAndEndArePresent() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.plusSeconds(250), null, null, null, null, PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isEqualTo(250);
        }

        @Test
        void calculateDurationShouldComputeZeroWhenStartEqualsEnd() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT, null, null, null, null, PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isZero();
        }

        @Test
        void calculateDurationYieldsNegativeWhenEndedAtIsBeforeStartedAt() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.minusSeconds(30), null, null, null, null, PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isEqualTo(-30);
        }

        @Test
        void calculateDurationTruncatesToSecondsDownwards() {
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.plusNanos(2_500_000_000L), null, null, null, null,
                    PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isEqualTo(2);
        }

        @Test
        void calculateDurationHandlesLargeButSafeDuration() {
            long seconds = 1_000_000_000L;
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.plus(Duration.ofSeconds(seconds)), null, null, null, null,
                    PARTICIPANTS);

            session.calculateDuration();

            assertThat(session.getDurationSeconds()).isEqualTo((int) seconds);
        }

        @Test
        void calculateDurationOverflowTruncatesToIntRangeWhenTooLarge() {
            long seconds = (long) Integer.MAX_VALUE + 10L;
            GameSession session = new GameSession(new GameSessionId("s"), new GameId("g"),
                    GameType.CHESS, new BuildingId("b"), GameStatus.IN_PROGRESS, STARTED_AT,
                    STARTED_AT.plus(Duration.ofSeconds(seconds)), null, null, null, null,
                    PARTICIPANTS);

            session.calculateDuration();

            int expected = (int) seconds;
            assertThat(session.getDurationSeconds()).isEqualTo(expected);
        }
    }

    @Nested
    class Equality {

        @Test
        void sessionsDoNotOverrideEqualsSoIdentityEqualityHolds() {
            GameSession a = sample();
            GameSession b = sample();
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
        }
    }
}
