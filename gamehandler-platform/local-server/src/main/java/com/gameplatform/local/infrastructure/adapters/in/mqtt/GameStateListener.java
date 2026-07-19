package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import com.gameplatform.local.domain.ports.in.UpdateGameStateUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import org.springframework.stereotype.Component;

/**
 * Listener MQTT per i messaggi contenenti aggiornamenti dello stato di una partita in corso.
 * <p>
 * Riceve i payload di stato dal broker MQTT, estrae l'identificativo del gioco dal topic e
 * delega l'aggiornamento al caso d'uso {@link UpdateGameStateUseCase}.
 * </p>
 *
 * @see UpdateGameStateUseCase
 */
@Component
public class GameStateListener {

    private final UpdateGameStateUseCase updateGameStateUseCase;

    /**
     * Costruisce un listener con il caso d'uso per l'aggiornamento dello stato di gioco.
     *
     * @param updateGameStateUseCase caso d'uso che applica la transizione di stato ricevuta
     */
    public GameStateListener(UpdateGameStateUseCase updateGameStateUseCase) {
        this.updateGameStateUseCase = updateGameStateUseCase;
    }

    /**
     * Elabora un messaggio MQTT contenente l'aggiornamento dello stato di una partita.
     * <p>
     * Estrae l'identificativo del gioco dal topic e deserializza il payload nello stato
     * richiesto, delegando la transizione a {@link UpdateGameStateUseCase}.
     * </p>
     *
     * @param topic   topic MQTT dal quale estrarre l'identificativo del gioco
     * @param payload payload del messaggio contenente il nuovo stato della partita
     * @throws NullPointerException se il payload è {@code null}
     * @throws com.gameplatform.local.domain.exception.InvalidGameStateTransitionException se la
     *         transizione di stato non è consentita (gestita internamente)
     * @throws com.gameplatform.local.domain.exception.ConcurrentStateException se un'altra
     *         operazione concorrente ha già modificato lo stato (gestita internamente)
     */
    public void handleStateMessage(String topic, byte[] payload) {
        if (payload == null) {
            throw new NullPointerException("Payload cannot be null");
        }
        GameStatePayload statePayload = MqttPayloadSerializer.deserialize(payload, GameStatePayload.class);
        
        // Extract gameId from topic building/{buildingId}/game/{gameId}/state
        String[] tokens = topic.split("/");
        String gameId = tokens[3];

        try {
            updateGameStateUseCase.updateState(new GameId(gameId), statePayload.status());
        } catch (com.gameplatform.local.domain.exception.InvalidGameStateTransitionException e) {
            org.slf4j.LoggerFactory.getLogger(GameStateListener.class)
                    .debug("Ignoring idempotent/no-op game state message on topic {}: {}", topic, e.getMessage());
        } catch (com.gameplatform.local.domain.exception.ConcurrentStateException e) {
            org.slf4j.LoggerFactory.getLogger(GameStateListener.class)
                    .warn("Concurrent game-state modification on topic {}; dropping message (another tx won): {}", topic, e.getMessage());
        }
    }
}
