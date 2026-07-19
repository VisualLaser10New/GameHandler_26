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


/**
 * Servizio di monitoraggio della connessione MQTT. Verifica periodicamente lo stato
 * della connessione e, in caso di perdita, tenta il recupero automatico ripristinando
 * lo stato del client e notificando i callback registrati.
 */
public class ConnectionMonitorService extends Service{

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitorService.class);

    private static final long MONITOR_INTERVAL_SECONDS = 15;
    /**
     * Callback per la notifica di eventi di connessione e riconnessione.
     */
    public interface ConnectionCallback {
        /**
         * Invocato dopo una riconnessione avvenuta con successo.
         */
        void onReconnected();

        /**
         * Invocato quando la connessione viene persa e il recupero non è ancora avvenuto.
         */
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

    private ScheduledFuture<?> monitorTask;

    /**
     * Costruisce un nuovo monitor di connessione.
     *
     * @param connectionManager il gestore della connessione MQTT, non null
     * @param heartbeatService  il servizio di heartbeat da sincronizzare con lo stato della connessione, non null
     */
    public ConnectionMonitorService(MqttConnectionManager connectionManager,
                                    HeartbeatService heartbeatService) {
        this.connectionManager = connectionManager;
        this.heartbeatService = heartbeatService;
    }

    /**
     * Avvia il monitoraggio periodico della connessione per il gioco specificato.
     *
     * @param gameId l'identificativo del gioco da monitorare, non null
     */
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

    /**
     * Arresta il monitoraggio della connessione e rilascia le risorse dello scheduler.
     */
    public void stop() {
        stopService();
        log.info("ConnectionMonitorService stopped");
    }

    /**
     * Registra il callback per la notifica di eventi di connessione e riconnessione.
     *
     * @param callback il callback da invocare, può essere null per rimuovere un callback precedente
     */
    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    /**
     * Aggiorna lo stato del client a {@link com.gameplatform.client.domain.ClientState#CONNECTED}.
     */
    public void onConnected() {
        clientState.set(ClientState.CONNECTED);
        log.info("Client connected — state: {}", clientState.get());
    }

    /**
     * Aggiorna lo stato del client a {@link com.gameplatform.client.domain.ClientState#LOGGED_IN}
     * e avvia l'heartbeat per il gioco corrente, se presente.
     */
    public void onLoggedIn() {
        clientState.set(ClientState.LOGGED_IN);
        if (currentGameId != null) {
            heartbeatService.startHeartbeat(currentGameId);
        }
        log.info("Client logged in — state: {}", clientState.get());
    }

    /**
     * Registra i dettagli della sessione di gioco attiva e aggiorna lo stato
     * del client a {@link com.gameplatform.client.domain.ClientState#IN_GAME}.
     *
     * @param sessionId    l'identificativo della sessione di gioco, non null
     * @param gameType     il tipo di gioco avviato, non null
     * @param participants la lista dei partecipanti alla sessione, non null
     */
    public void onGameStarted(GameSessionId sessionId, GameType gameType, List<String> participants) {
        this.activeSessionId = sessionId;
        this.activeGameType = gameType;
        this.activeParticipants = participants;
        clientState.set(ClientState.IN_GAME);
        log.info("Game started — sessionId: {}, state: {}", sessionId.value(), clientState.get());
    }

    /**
     * Aggiorna lo stato del client a {@link com.gameplatform.client.domain.ClientState#PAUSED}.
     */
    public void onGamePaused() {
        clientState.set(ClientState.PAUSED);
        log.info("Game paused — state: {}", clientState.get());
    }

    /**
     * Ripristina lo stato del client a {@link com.gameplatform.client.domain.ClientState#IN_GAME}.
     */
    public void onGameResumed() {
        clientState.set(ClientState.IN_GAME);
        log.info("Game resumed — state: {}", clientState.get());
    }

    /**
     * Resetta i dettagli della sessione attiva e riporta lo stato del client
     * a {@link com.gameplatform.client.domain.ClientState#LOGGED_IN}.
     */
    public void onGameStopped() {
        activeSessionId = null;
        activeGameType = null;
        activeParticipants = null;
        clientState.set(ClientState.LOGGED_IN);
        log.info("Game stopped — state: {}", clientState.get());
    }

    /**
     * Restituisce lo stato corrente del client.
     *
     * @return lo stato del client, mai null
     */
    public ClientState getClientState() {
        return clientState.get();
    }

    /**
     * Restituisce l'identificativo della sessione di gioco attiva.
     *
     * @return l'identificativo della sessione, o null se nessuna sessione è in corso
     */
    public GameSessionId getActiveSessionId() {
        return activeSessionId;
    }

    /**
     * Verifica lo stato della connessione MQTT. Se la connessione è attiva, non esegue
     * alcuna operazione. In caso di connessione persa, arresta l'heartbeat, aggiorna lo
     * stato a {@link com.gameplatform.client.domain.ClientState#DISCONNECTED}, notifica
     * il callback di perdita connessione, tenta la riconnessione tramite il connection manager
     * e, in caso di successo, riavvia l'heartbeat, ripristina lo stato precedente e notifica
     * il callback di riconnessione.
     */
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