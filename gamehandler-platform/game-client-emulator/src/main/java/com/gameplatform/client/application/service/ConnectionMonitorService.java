package com.gameplatform.client.application.service;

import com.gameplatform.client.domain.ClientState;
import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.client.infrastructure.mqtt.MqttConnectionManager;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class ConnectionMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitorService.class);

    private static final long MONITOR_INTERVAL_SECONDS = 15;
    public interface ConnectionCallback {
        /** Called after a successful reconnection. */
        void onReconnected();

        /** Called when the connection is lost and recovery has not yet succeeded. */
        void onConnectionLost();
    }
    private final MqttConnectionManager connectionManager;
    private final HeartbeatService heartbeatService;
    private final AtomicReference<ClientState> clientState =
            new AtomicReference<>(ClientState.DISCONNECTED);
    private volatile String currentGameId;
    private volatile GameSessionId activeSessionId;
    private volatile GameType activeGameType;
    private volatile List<String> activeParticipants;
    private ConnectionCallback connectionCallback;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> monitorTask;

    public ConnectionMonitorService(MqttConnectionManager connectionManager,
                                    HeartbeatService heartbeatService) {
        this.connectionManager = connectionManager;
        this.heartbeatService = heartbeatService;
    }

    public void start(String gameId) {
        this.currentGameId = gameId;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "connection-monitor");
            t.setDaemon(true);
            return t;
        });

        monitorTask = scheduler.scheduleAtFixedRate(
                this::checkConnection,
                MONITOR_INTERVAL_SECONDS,
                MONITOR_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("ConnectionMonitorService started for game {}", gameId);
    }

    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel(false);
            monitorTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        log.info("ConnectionMonitorService stopped");
    }

    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    public void onConnected() {
        clientState.set(ClientState.CONNECTED);
        log.info("Client connected — state: {}", clientState.get());
    }

    public void onLoggedIn() {
        clientState.set(ClientState.LOGGED_IN);
        if (currentGameId != null) {
            heartbeatService.startHeartbeat(currentGameId);
        }
        log.info("Client logged in — state: {}", clientState.get());
    }

    public void onGameStarted(GameSessionId sessionId, GameType gameType, List<String> participants) {
        this.activeSessionId = sessionId;
        this.activeGameType = gameType;
        this.activeParticipants = participants;
        clientState.set(ClientState.IN_GAME);
        log.info("Game started — sessionId: {}, state: {}", sessionId.value(), clientState.get());
    }

    public void onGamePaused() {
        clientState.set(ClientState.PAUSED);
        log.info("Game paused — state: {}", clientState.get());
    }

    public void onGameResumed() {
        clientState.set(ClientState.IN_GAME);
        log.info("Game resumed — state: {}", clientState.get());
    }

    public void onGameStopped() {
        activeSessionId = null;
        activeGameType = null;
        activeParticipants = null;
        clientState.set(ClientState.LOGGED_IN);
        log.info("Game stopped — state: {}", clientState.get());
    }

    public ClientState getClientState() {
        return clientState.get();
    }

    public GameSessionId getActiveSessionId() {
        return activeSessionId;
    }

    private void checkConnection() {
        if (connectionManager.isConnected()) {
            return; // all good, nothing to do
        }

        log.warn("MQTT connection lost — current state: {}", clientState.get());
        heartbeatService.stopHeartbeat();
        clientState.set(ClientState.DISCONNECTED);

        if (connectionCallback != null) {
            try {
                connectionCallback.onConnectionLost();
            } catch (Exception e) {
                log.warn("Error in onConnectionLost callback", e);
            }
        }

        // MqttConnectionManager.start() handles retry internally
        connectionManager.start();

        if (connectionManager.isConnected()) {
            log.info("MQTT connection recovered — resuming heartbeat");
            if (currentGameId != null) {
                heartbeatService.startHeartbeat(currentGameId);
            }
            // Restore a meaningful state after reconnect
            if (activeSessionId != null) {
                clientState.set(ClientState.IN_GAME);
            } else {
                clientState.set(ClientState.CONNECTED);
            }

            if (connectionCallback != null) {
                try {
                    connectionCallback.onReconnected();
                } catch (Exception e) {
                    log.warn("Error in onReconnected callback", e);
                }
            }
        }
    }
}