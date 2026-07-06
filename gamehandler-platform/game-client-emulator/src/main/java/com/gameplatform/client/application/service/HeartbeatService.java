package com.gameplatform.client.application.service;

import com.gameplatform.client.infrastructure.mqtt.HeartbeatPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HeartbeatService extends Service {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5;
    private final HeartbeatPublisher heartbeatPublisher;
    private volatile String currentGameId;

    public HeartbeatService(HeartbeatPublisher heartbeatPublisher) {
        this.heartbeatPublisher = heartbeatPublisher;
    }

    public void startHeartbeat(String gameId) {
        stopHeartbeat(); // ensure clean state before starting

        this.currentGameId = gameId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler-" + gameId);
            t.setDaemon(true);
            return t;
        });

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::handleHeartbeat,
                0,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("Heartbeat started for game {} (interval: {}s)", gameId, HEARTBEAT_INTERVAL_SECONDS);
    }

    public void stopHeartbeat() {
        stopService();
        log.info("Heartbeat stopped for game {}", currentGameId);
        currentGameId = null;
    }



    public void handleHeartbeat() {
        if (currentGameId == null) {
            log.warn("handleHeartbeat called but no game is currently tracked");
            return;
        }
        log.debug("Sending heartbeat for game {}", currentGameId);
        heartbeatPublisher.publishHeartbeat(currentGameId);
    }

    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}