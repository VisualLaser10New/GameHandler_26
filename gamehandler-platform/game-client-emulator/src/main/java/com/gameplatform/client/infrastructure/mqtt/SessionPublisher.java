package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.SessionEndPayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionResumePayload;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Pubblica gli eventi del ciclo di vita delle sessioni di gioco sul broker MQTT.
 * <p>
 * Supporta quattro tipi di eventi di sessione: avvio, termine, pausa e ripresa,
 * oltre a eventi di lobby (creazione, partecipazione, avvio, cancellazione,
 * abbandono) ed eventi di gioco multiplayer (turno, mossa, punteggio).
 * Ogni metodo costruisce il payload e il topic appropriato utilizzando i metodi
 * di supporto di {@link MqttTopics} e pubblica con QoS 1 (non retained)
 * tramite {@link MqttClientAdapter}.
 */
public class SessionPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionPublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Costruisce un publisher di sessione per l'adapter e l'edificio specificati.
     *
     * @param adapter    l'adapter MQTT utilizzato per la pubblicazione
     * @param buildingId l'identificativo dell'edificio per la costruzione del topic
     */
    public SessionPublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Pubblica un evento di avvio sessione.
     *
     * @param gameId       l'identificativo della macchina da gioco
     * @param sessionId    l'identificativo della sessione
     * @param gameType     il tipo di gioco avviato
     * @param participants la lista degli identificativi dei partecipanti
     */
    public void publishStart(String gameId, String sessionId, GameType gameType, List<String> participants) {
        try {
            String topic = MqttTopics.sessionStart(buildingId, gameId);
            SessionStartPayload payload = new SessionStartPayload(sessionId, gameType, participants);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session start to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session start", e);
        }
    }

    /**
     * Pubblica un evento di termine sessione con i risultati.
     *
     * @param gameId       l'identificativo della macchina da gioco
     * @param sessionId    l'identificativo della sessione
     * @param winnerId     l'identificativo del vincitore, oppure {@code null} in caso di pareggio
     * @param winCondition la modalit&agrave; di conclusione della sessione (es. WIN, DRAW, TIMEOUT)
     * @param resultData   stringa JSON opzionale con risultati dettagliati; pu&ograve; essere {@code null}
     */
    public void publishEnd(String gameId, String sessionId, String winnerId,
                           WinCondition winCondition, String resultData) {
        try {
            String topic = MqttTopics.sessionEnd(buildingId, gameId);
            SessionEndPayload payload = new SessionEndPayload(sessionId, winnerId, winCondition, resultData);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session end to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session end", e);
        }
    }

    /**
     * Pubblica un evento di pausa sessione.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     * @param pausedBy  l'utente che ha messo in pausa la sessione, oppure {@code null}
     */
    public void publishPause(String gameId, String sessionId, String pausedBy) {
        try {
            String topic = MqttTopics.sessionPause(buildingId, gameId);
            SessionPausePayload payload = new SessionPausePayload(sessionId, pausedBy);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session pause to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session pause", e);
        }
    }

    /**
     * Pubblica un evento di ripresa sessione.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     */
    public void publishResume(String gameId, String sessionId) {
        try {
            String topic = MqttTopics.sessionResume(buildingId, gameId);
            SessionResumePayload payload = new SessionResumePayload(sessionId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session resume to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session resume", e);
        }
    }

    /**
     * Pubblica un evento di creazione lobby.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param gameType  il tipo di gioco della lobby
     * @param creatorId l'identificativo dell'utente che ha creato la lobby
     */
    public void publishLobbyCreate(String gameId, GameType gameType, String creatorId) {
        try {
            String topic = "building/" + buildingId + "/game/" + gameId + "/session/lobby/create";
            com.gameplatform.shared.mqtt.payload.LobbyCreatePayload payload = new com.gameplatform.shared.mqtt.payload.LobbyCreatePayload(gameType, creatorId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing lobby create to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish lobby create", e);
        }
    }

    /**
     * Pubblica un evento di partecipazione alla lobby.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     * @param userId    l'identificativo dell'utente che si unisce alla lobby
     */
    public void publishLobbyJoin(String gameId, String sessionId, String userId) {
        try {
            String topic = "building/" + buildingId + "/game/" + gameId + "/session/lobby/join";
            com.gameplatform.shared.mqtt.payload.LobbyJoinPayload payload = new com.gameplatform.shared.mqtt.payload.LobbyJoinPayload(sessionId, userId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing lobby join to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish lobby join", e);
        }
    }

    /**
     * Pubblica un evento di avvio della partita dalla lobby.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     */
    public void publishLobbyStart(String gameId, String sessionId) {
        try {
            String topic = "building/" + buildingId + "/game/" + gameId + "/session/lobby/start";
            com.gameplatform.shared.mqtt.payload.LobbyStartPayload payload = new com.gameplatform.shared.mqtt.payload.LobbyStartPayload(sessionId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing lobby start to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish lobby start", e);
        }
    }

    /**
     * Pubblica un evento di cancellazione della lobby.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     * @param userId    l'identificativo dell'utente che ha cancellato la lobby
     */
    public void publishLobbyCancel(String gameId, String sessionId, String userId) {
        try {
            String topic = "building/" + buildingId + "/game/" + gameId + "/session/lobby/cancel";
            com.gameplatform.shared.mqtt.payload.LobbyCancelPayload payload = new com.gameplatform.shared.mqtt.payload.LobbyCancelPayload(sessionId, userId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing lobby cancel to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish lobby cancel", e);
        }
    }

    /**
     * Pubblica un evento di abbandono della lobby.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     * @param userId    l'identificativo dell'utente che abbandona la lobby
     */
    public void publishLobbyLeave(String gameId, String sessionId, String userId) {
        try {
            String topic = "building/" + buildingId + "/game/" + gameId + "/session/lobby/leave";
            com.gameplatform.shared.mqtt.payload.LobbyLeavePayload payload = new com.gameplatform.shared.mqtt.payload.LobbyLeavePayload(sessionId, userId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing lobby leave to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish lobby leave", e);
        }
    }

    /**
     * Pubblica un evento di cambio turno per mantenere sincronizzati tutti
     * gli emulatori partecipanti a un gioco multiplayer a turni.
     * <p>
     * Inviato peer-to-peer tra i client (il server locale non gestisce la
     * logica del turno); pubblicato su {@link MqttTopics#sessionTurn} con QoS 1.
     *
     * @param gameId     l'identificativo della macchina da gioco
     * @param sessionId  l'identificativo della sessione
     * @param turnIndex  l'indice del nuovo turno (base 0) nella lista dei partecipanti
     * @param playerName il nome dell'utente di cui è il turno
     */
    public void publishTurn(String gameId, String sessionId, int turnIndex, String playerName) {
        try {
            String topic = MqttTopics.sessionTurn(buildingId, gameId);
            com.gameplatform.shared.mqtt.payload.TurnPayload payload =
                    new com.gameplatform.shared.mqtt.payload.TurnPayload(sessionId, turnIndex, playerName);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing turn update to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish turn update", e);
        }
    }

    /**
     * Pubblica un evento di mossa sulla scacchiera per sincronizzare lo stato
     * del gioco tra tutti gli emulatori partecipanti a un gioco multiplayer
     * a scacchiera (attualmente Chess).
     * <p>
     * Inviato peer-to-peer tra i client su {@link MqttTopics#sessionMove} con QoS 1.
     *
     * @param gameId        l'identificativo della macchina da gioco
     * @param sessionId     l'identificativo della sessione
     * @param fromRow       riga di origine (base 0)
     * @param fromCol       colonna di origine (base 0)
     * @param toRow         riga di destinazione (base 0)
     * @param toCol         colonna di destinazione (base 0)
     * @param capturedPiece glifo Unicode del pezzo catturato sulla cella di
     *                      destinazione, oppure {@code null} se la cella era vuota
     */
    public void publishMove(String gameId, String sessionId,
                            int fromRow, int fromCol, int toRow, int toCol,
                            String capturedPiece) {
        try {
            String topic = MqttTopics.sessionMove(buildingId, gameId);
            com.gameplatform.shared.mqtt.payload.MovePayload payload =
                    new com.gameplatform.shared.mqtt.payload.MovePayload(
                            sessionId, fromRow, fromCol, toRow, toCol, capturedPiece);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing move to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish move", e);
        }
    }

    /**
     * Pubblica un'istantanea dei punteggi per sincronizzare la classifica tra
     * tutti gli emulatori partecipanti a un gioco multiplayer.
     * <p>
     * Inviato peer-to-peer tra i client su {@link MqttTopics#sessionScore} con QoS 1.
     *
     * @param gameId    l'identificativo della macchina da gioco
     * @param sessionId l'identificativo della sessione
     * @param scores    mappa completa delle voci giocatore-punteggio
     */
    public void publishScore(String gameId, String sessionId,
                             java.util.Map<String, Integer> scores) {
        try {
            String topic = MqttTopics.sessionScore(buildingId, gameId);
            com.gameplatform.shared.mqtt.payload.ScorePayload payload =
                    new com.gameplatform.shared.mqtt.payload.ScorePayload(sessionId, scores);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            log.info("Publishing score to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish score", e);
        }
    }
}
