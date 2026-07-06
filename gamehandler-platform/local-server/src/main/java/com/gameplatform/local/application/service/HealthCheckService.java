package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.AlertPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R3 (outbox atomicity) — the sweep no longer holds a single class-level tx
 * across ALL games. The class-level {@code @Transactional} was changed to
 * {@link Propagation#NEVER} so the per-game abort runs inside its OWN
 * {@link Propagation#REQUIRES_NEW} transaction on a separate bean
 * ({@link SessionAbortHelper#abortAndEmit}). If that bean throws (outbox save
 * failure, serialization failure, transition guard), the inner tx rolls back
 * entirely → session NOT aborted, game NOT released, NO outbox row — the
 * contract the previous inline {@code try { ... } catch (Exception e)
 * { log.error(...); }} silently violated (it swallowed the failure and let
 * the outer sweep tx commit the abort WITHOUT the outbox row). Each game's
 * abort is now logged-and-skipped; the next tick retries.
 */
@Service
@Transactional(propagation = Propagation.NEVER)
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final PublishAlertPort publishAlertPort;
    private final Clock clock;
    private final SessionAbortHelper sessionAbortHelper;

    // Tracks responded clients within the current 5-minute cycle
    private final ConcurrentHashMap<GameId, Boolean> respondedInCycle = new ConcurrentHashMap<>();

    // Tracks consecutive missed heartbeats for each game machine
    private final ConcurrentHashMap<GameId, Integer> missedHeartbeatsMap = new ConcurrentHashMap<>();

    public HealthCheckService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            PublishGameStatePort publishGameStatePort,
            PublishAlertPort publishAlertPort,
            Clock clock,
            SessionAbortHelper sessionAbortHelper) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.publishAlertPort = publishAlertPort;
        this.clock = clock;
        this.sessionAbortHelper = sessionAbortHelper;
    }

    private void deferMqttPublish(Runnable publishRunnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishRunnable.run();
                        } catch (Exception e) {
                            log.error("Failed to execute deferred MQTT publication", e);
                        }
                    }
                }
            );
        } else {
            try {
                publishRunnable.run();
            } catch (Exception e) {
                log.error("Failed to execute MQTT publication", e);
            }
        }
    }

    @Scheduled(fixedRateString = "${app.healthcheck-interval-ms:300000}")
    public void performHealthCheck() {
        List<Game> games = gameRepository.findAll();

        for (Game game : games) {
            GameId gameId = game.getId();
            
            // Check if client responded since last health check cycle
            boolean responded = respondedInCycle.getOrDefault(gameId, false);

            if (!responded) {
                int missed = missedHeartbeatsMap.getOrDefault(gameId, 0) + 1;
                missedHeartbeatsMap.put(gameId, missed);

                // If client failed to respond for 3 consecutive cycles (15 minutes), declare unreachable
                if (missed >= 3) {
                    missedHeartbeatsMap.put(gameId, 0);

                    // Abort any active session. The abort + game release +
                    // GAME_SESSION_ABORTED outbox row happen atomically inside a
                    // REQUIRES_NEW tx on the SessionAbortHelper bean. A failure
                    // (outbox save, serialization, transition guard) propagates
                    // and rolls back the ENTIRE inner tx → session NOT aborted,
                    // game NOT released, NO outbox row. We log-and-skip; the next
                    // tick retries. This replaces the previous inline try/catch
                    // that swallowed failures and let the class-level sweep tx
                    // commit the abort WITHOUT the outbox row (R3 root cause).
                    Optional<GameSession> activeSessionOpt = gameSessionRepository.findActiveByGameId(gameId);
                    if (activeSessionOpt.isPresent()) {
                        GameSession session = activeSessionOpt.get();
                        try {
                            sessionAbortHelper.abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT");
                        } catch (Exception e) {
                            log.error("Per-game abort+outbox failed for gameId={}; tx rolled back, will retry next tick", gameId, e);
                        }
                    } else {
                        // No active session, but the machine may still be stuck
                        // IN_USE / LOBBY (a disconnected client leaves the
                        // machine in either state). Previously this branch ran
                        // unconditionally; with the abort now owning its own tx
                        // the standalone release lives in the no-session case.
                        if (game.getStatus() == GameMachineStatus.IN_USE
                                || game.getStatus() == GameMachineStatus.LOBBY) {
                            game.release();
                            gameRepository.save(game);
                            deferMqttPublish(() -> publishGameStatePort.publishState(gameId, game.getStatus()));
                        }
                    }

                    // Publish alert to MQTT
                    AlertPayload alert = new AlertPayload(
                            "UNREACHABLE",
                            gameId.id(),
                            "Client has missed 3 consecutive heartbeat cycles (15 minutes). Declaring unreachable.",
                            Instant.now(clock)
                    );
                    deferMqttPublish(() -> publishAlertPort.publishAlert(alert));
                }
            } else {
                // Reset missed counter on successful contact
                missedHeartbeatsMap.put(gameId, 0);
            }

            // Reset response flag for the next cycle
            respondedInCycle.put(gameId, false);

            // Send new heartbeat ping to client via MQTT session event topic
            String topic = MqttTopics.heartbeat(game.getBuildingId().id(), gameId.id());
            deferMqttPublish(() -> publishGameStatePort.publishSessionEvent(topic, "PING"));
        }
    }

    public void registerHeartbeat(GameId gameId) {
        respondedInCycle.put(gameId, true);
    }
}