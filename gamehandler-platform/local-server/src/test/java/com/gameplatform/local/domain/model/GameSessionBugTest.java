package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests exposing bugs in the GameSession domain model.
 */
class GameSessionBugTest {

    private static final GameSessionId SESSION_ID = new GameSessionId("sess-1");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("bld-1");

    /**
     * BUG #1: calculateDuration() produces negative values when endedAt < startedAt.
     *
     * In GameSession.calculateDuration() (line 133), Duration.between(startedAt, endedAt).toSeconds()
     * will return a negative value if endedAt is before startedAt. This can happen due to clock
     * skew or bugs in callers. The domain model does NOT guard against this.
     *
     * Impact: Negative duration stored in database, corrupts statistics.
     */
    @Test
    @DisplayName("BUG #1: calculateDuration produces negative durationSeconds when endedAt < startedAt")
    void calculateDuration_negativeWhenEndedAtBeforeStartedAt() {
        Instant startedAt = Instant.parse("2025-01-01T12:00:00Z");
        Instant endedAtBefore = startedAt.minus(Duration.ofMinutes(5));

        GameSession session = new GameSession(
                SESSION_ID, GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, startedAt, null, null,
                null, null, null, List.of()
        );

        // Complete with endedAt BEFORE startedAt
        session.complete(null, endedAtBefore);

        // BUG: durationSeconds is negative (-300)
        assertNotNull(session.getDurationSeconds());
        assertTrue(session.getDurationSeconds() < 0,
                "BUG CONFIRMED: durationSeconds is negative: " + session.getDurationSeconds());
    }

    /**
     * BUG #2: complete() allows ABORTED -> COMPLETED transition.
     *
     * In GameSession.complete() (line 83), the guard condition explicitly allows status == ABORTED:
     *   if (this.status != GameStatus.IN_PROGRESS && this.status != GameStatus.PAUSED && this.status != GameStatus.ABORTED)
     *
     * An ABORTED session should be a terminal state. Allowing ABORTED -> COMPLETED creates
     * inconsistent state: a session was abort-finalized (machine released) then completed.
     *
     * The GameSessionService.end() exploits this at line 156 ("Late arrival handling"),
     * but the domain model should not allow this transition inherently for other callers.
     *
     * Impact: Sessions that were aborted (and their game machines released) can be re-completed,
     * creating conflicting outbox events and potential double game-machine state changes.
     */
    @Test
    @DisplayName("BUG #2: ABORTED -> COMPLETED transition is allowed by domain model")
    void complete_allowsAbortedToCompleted() {
        GameSession session = new GameSession(
                SESSION_ID, GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, Instant.now(), null, null,
                null, null, null, List.of()
        );

        session.abort(StopReason.ABORTED, Instant.now());
        assertEquals(GameStatus.ABORTED, session.getStatus());

        // BUG: This should throw but doesn't - ABORTED is a terminal state
        assertDoesNotThrow(() -> session.complete(null, Instant.now()),
                "BUG CONFIRMED: ABORTED -> COMPLETED transition is permitted");
        assertEquals(GameStatus.COMPLETED, session.getStatus());
    }

    /**
     * BUG #3: Constructing GameSession with ArrayList bypasses immutability.
     *
     * The secondary constructor (line 72) passes `new ArrayList<>()` to the primary constructor.
     * The primary constructor (line 65) wraps it with List.copyOf(). However, if external code
     * constructs a GameSession with the primary constructor passing a mutable list reference,
     * the List.copyOf() ensures safety. But the REAL problem is that `getParticipants()` (line 182)
     * makes a SECOND copy via List.copyOf(), which means every call creates a new list -
     * slightly wasteful but functionally correct. The constructor itself is fine.
     *
     * The TRUE bug is subtler: GameSession is immutable for participants but doesn't protect
     * the list from being empty when participants are required for competitive games.
     * No validation that participants.size() >= minPlayers is performed.
     */
    @Test
    @DisplayName("BUG #3: No validation on minimum participants for competitive games")
    void noMinimumParticipantsValidation() {
        // Creating a competitive game session (e.g., CHESS) with zero participants is allowed
        GameSession session = new GameSession(
                SESSION_ID, GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, Instant.now(), null, null,
                null, null, null, List.of()  // empty participants for a 2-player game
        );

        // BUG: No validation - allows starting a session without any participants
        assertTrue(session.getParticipants().isEmpty(),
                "BUG CONFIRMED: CHESS session created with 0 participants");
    }
}
