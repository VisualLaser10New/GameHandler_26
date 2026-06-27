package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import com.gameplatform.local.domain.ports.in.UpdateGameStateUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import org.springframework.stereotype.Component;

@Component
public class GameStateListener {

    private final UpdateGameStateUseCase updateGameStateUseCase;

    public GameStateListener(UpdateGameStateUseCase updateGameStateUseCase) {
        this.updateGameStateUseCase = updateGameStateUseCase;
    }

    public void handleStateMessage(String topic, byte[] payload) {
        if (payload == null) {
            throw new NullPointerException("Payload cannot be null");
        }
        GameStatePayload statePayload = MqttPayloadSerializer.deserialize(payload, GameStatePayload.class);
        
        // Extract gameId from topic building/{buildingId}/game/{gameId}/state
        String[] tokens = topic.split("/");
        String gameId = tokens[3];

        updateGameStateUseCase.updateState(new GameId(gameId), statePayload.status());
    }
}
