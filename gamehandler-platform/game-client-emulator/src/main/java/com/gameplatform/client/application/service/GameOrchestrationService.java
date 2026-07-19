package com.gameplatform.client.application.service;

import com.gameplatform.shared.domain.game.GameFactory;
import com.gameplatform.shared.domain.game.GameLifecycle;
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


/**
 * Servizio di orchestrazione del ciclo di vita di una partita.
 * Gestisce l'avvio, la pausa, la ripresa e l'arresto di una partita,
 * coordinandosi con il server locale tramite publisher MQTT e aggiornando
 * lo stato del monitor di connessione.
 */
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

    /**
     * Costruisce un nuovo orchestratore di partite.
     *
     * @param sessionPublisher  il publisher MQTT per gli eventi di sessione, non null
     * @param connectionMonitor il monitor di connessione da notificare, non null
     * @param gameId            l'identificativo del gioco gestito, non null
     */
    public GameOrchestrationService(SessionPublisher sessionPublisher,
                                    ConnectionMonitorService connectionMonitor,
                                    String gameId) {
        this.sessionPublisher = sessionPublisher;
        this.connectionMonitor = connectionMonitor;
        this.gameId = gameId;
    }

    /**
     * Avvia una nuova partita del tipo specificato con i partecipanti indicati.
     * Pubblica una richiesta di avvio sessione, attende la conferma dal server,
     * crea il dominio di gioco e notifica il monitor di connessione.
     *
     * @param type         il tipo di gioco da avviare, non null
     * @param participants la lista dei partecipanti alla partita, non null
     * @throws IllegalStateException se una partita è già in corso
     * @throws RuntimeException      se la conferma della sessione non arriva entro il timeout o
     *                               se si verifica un errore durante l'attesa
     */
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

    /**
     * Arresta la partita in corso con il motivo specificato e pubblica l'evento di fine sessione.
     * Se nessuna partita è in corso, registra un avviso e non esegue alcuna operazione.
     *
     * @param reason       il motivo dell'arresto, non null
     * @param winnerId     l'identificativo del vincitore, può essere null in caso di pareggio o interruzione
     * @param winCondition la condizione di vittoria, non null
     * @param resultData   dati aggiuntivi sul risultato, può essere null
     */
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

    /**
     * Arresta la partita in corso con il motivo specificato, utilizzando valori predefiniti
     * per vincitore (null), condizione di vittoria ({@link WinCondition#DRAW}) e dati risultato (null).
     *
     * @param reason il motivo dell'arresto, non null
     * @see #stopGame(StopReason, String, WinCondition, String)
     */
    public void stopGame(StopReason reason) {
        stopGame(reason, null, WinCondition.DRAW, null);
    }

    /**
     * Azzera lo stato interno della partita senza pubblicare eventi MQTT di fine sessione.
     * Utilizzato quando il giocatore remoto termina l'incontro: il client locale deve
     * rilasciare lo stato per consentire l'avvio di una nuova partita, senza ripubblicare
     * l'evento di fine (che causerebbe un ciclo di eco o doppia elaborazione sul server).
     */
    public void forceClear() {
        log.info("Force-clearing game state (remote end). Session was: {}",
                currentSessionId != null ? currentSessionId.value() : "none");
        currentGame = null;
        currentSessionId = null;
        currentGameType = null;
        currentParticipants = null;
        pendingStarts.clear();
    }

    /**
     * Mette in pausa la partita in corso.
     * Se nessuna partita è attiva, registra un avviso e non esegue alcuna operazione.
     */
    public void pauseGame() {
        if (currentGame == null) {
            log.warn("pauseGame called but no game is currently running");
            return;
        }
        currentGame.pause();
    }

    /**
     * Riprende la partita precedentemente messa in pausa.
     * Se nessuna partita è attiva, registra un avviso e non esegue alcuna operazione.
     */
    public void resumeGame() {
        if (currentGame == null) {
            log.warn("resumeGame called but no game is currently running");
            return;
        }
        currentGame.resume();
    }

    /**
     * Gestisce la conferma di avvio sessione ricevuta dal server.
     * Deserializza il payload, estrae l'identificativo della sessione e completa
     * la richiesta pendente di avvio partita. Se il payload non contiene un
     * identificativo valido, ignora la conferma. In caso di errore di deserializzazione,
     * completa eccezionalmente la richiesta pendente.
     *
     * @param payload i dati binari della conferma, non null
     */
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

    /**
     * Restituisce il modello del dominio di gioco della partita corrente.
     *
     * @return l'istanza del gioco corrente, o null se nessuna partita è in corso
     */
    public GameLifecycle getCurrentGame() {
        return currentGame;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco corrente.
     *
     * @return l'identificativo della sessione, o null se nessuna partita è in corso
     */
    public GameSessionId getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * Verifica se una partita è attualmente in corso.
     *
     * @return true se una partita è attiva, false altrimenti
     */
    public boolean isGameInProgress() {
        return currentGame != null;
    }
}