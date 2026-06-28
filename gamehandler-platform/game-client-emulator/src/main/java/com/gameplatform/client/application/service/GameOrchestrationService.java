package com.gameplatform.client.application.service;

import com.gameplatform.client.domain.GameFactory;
import com.gameplatform.client.domain.GameLifecycle;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class GameOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(GameOrchestrationService.class);
    private static final long SESSION_CONFIRM_TIMEOUT_SECONDS = 10;
    private final SessionPublisher sessionPublisher;
    private final ConnectionMonitorService connectionMonitor;
    private final String gameId;
    private volatile GameLifecycle currentGame;
    private volatile GameSessionId currentSessionId;
    private volatile GameType currentGameType;
    private volatile List<String> currentParticipants;

    private final ConcurrentHashMap<String, CompletableFuture<GameSessionId>> pendingStarts =
            new ConcurrentHashMap<>();

    public GameOrchestrationService(SessionPublisher sessionPublisher,
                                    ConnectionMonitorService connectionMonitor,
                                    String gameId) {
        this.sessionPublisher = sessionPublisher;
        this.connectionMonitor = connectionMonitor;
        this.gameId = gameId;
    }

    public void startGame(GameType type, List<String> participants) {
        if (currentGame != null) {
            throw new IllegalStateException("A game is already in progress. Stop it first.");
        }

        this.currentGameType = type;
        this.currentParticipants = participants;

        // Register a future BEFORE publishing so we don't miss the server reply
        CompletableFuture<GameSessionId> sessionFuture = new CompletableFuture<>();
        pendingStarts.put(gameId, sessionFuture);

        log.info("Publishing session start for game {} (type: {}, {} participants)",
                gameId, type, participants.size());

        // The sessionId field in the outbound message is intentionally empty —
        // the server will assign it and echo it back.
        sessionPublisher.publishStart(gameId, "", type, participants);

        // Block until the server confirms the session ID (or timeout)
        GameSessionId confirmedSessionId;
        try {
            confirmedSessionId = sessionFuture.get(SESSION_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingStarts.remove(gameId);
            throw new RuntimeException(
                    "Timed out waiting for session confirmation from local-server for game " + gameId, e);
        } catch (InterruptedException e) {
            pendingStarts.remove(gameId);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for session confirmation", e);
        } catch (Exception e) {
            pendingStarts.remove(gameId);
            throw new RuntimeException("Failed to obtain session ID from local-server", e);
        }

        log.info("Session confirmed by server — sessionId: {}", confirmedSessionId.value());

        // Create the game domain object with the real, DB-assigned session ID
        currentSessionId = confirmedSessionId;
        currentGame = GameFactory.createGame(type, confirmedSessionId);

        List<UserId> userIds = participants.stream()
                .map(UserId::new)
                .toList();
        currentGame.start(userIds);

        // Notify the connection monitor so it can update ClientState → IN_GAME
        connectionMonitor.onGameStarted(confirmedSessionId, type, participants);

        log.info("Game {} started successfully (sessionId: {})", type, confirmedSessionId.value());
    }

    public void stopGame(StopReason reason, String winnerId, WinCondition winCondition, String resultData) {
        if (currentGame == null) {
            log.warn("stopGame called but no game is currently running");
            return;
        }

        log.info("Stopping game (sessionId: {}, reason: {})", currentSessionId.value(), reason);
        currentGame.stop(reason);

        sessionPublisher.publishEnd(gameId, currentSessionId.value(), winnerId, winCondition, resultData);
        connectionMonitor.onGameStopped();

        currentGame = null;
        currentSessionId = null;
        currentGameType = null;
        currentParticipants = null;
    }

    public void stopGame(StopReason reason) {
        stopGame(reason, null, WinCondition.DRAW, null);
    }

    public void pauseGame() {
        if (currentGame == null) {
            log.warn("pauseGame called but no game is currently running");
            return;
        }
        currentGame.pause();
    }

    public void resumeGame() {
        if (currentGame == null) {
            log.warn("resumeGame called but no game is currently running");
            return;
        }
        currentGame.resume();
    }

    public void onSessionStartConfirmed(byte[] payload) {
        try {
            SessionStartPayload startPayload = MqttPayloadSerializer.deserialize(payload, SessionStartPayload.class);

            if (startPayload.sessionId() == null || startPayload.sessionId().isBlank()) {
                log.warn("Received session/start confirmation with blank sessionId — ignoring");
                return;
            }

            GameSessionId confirmedId = new GameSessionId(startPayload.sessionId());
            log.info("Received session start confirmation from server: sessionId={}", confirmedId.value());

            CompletableFuture<GameSessionId> future = pendingStarts.remove(gameId);
            if (future != null) {
                future.complete(confirmedId);
            } else {
                log.warn("No pending session start found for game {} — confirmation ignored", gameId);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize session start confirmation payload", e);
            CompletableFuture<GameSessionId> future = pendingStarts.remove(gameId);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    public GameLifecycle getCurrentGame() {
        return currentGame;
    }

    public GameSessionId getCurrentSessionId() {
        return currentSessionId;
    }

    public boolean isGameInProgress() {
        return currentGame != null;
    }
}