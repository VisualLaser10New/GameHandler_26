package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.ports.in.UpdateGameStateUseCase;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameStateListenerTest {

    @Mock
    private UpdateGameStateUseCase updateGameStateUseCase;
    @InjectMocks
    private GameStateListener listener;

    @Test
    void shouldDeserializeAndCallUpdateState() {
        String topic = "building/b1/game/g1/state";
        byte[] payload = MqttPayloadSerializer.serialize(new GameStatePayload("g1", GameMachineStatus.IN_USE, null));

        listener.handleStateMessage(topic, payload);

        verify(updateGameStateUseCase).updateState(eq(new com.gameplatform.shared.domain.model.GameId("g1")),
                eq(GameMachineStatus.IN_USE));
    }

    @Test
    void shouldUseGameIdFromTopicNotFromPayload() {
        String topic = "building/b1/game/from-topic/state";
        byte[] payload = MqttPayloadSerializer.serialize(
                new GameStatePayload("from-payload", GameMachineStatus.RESERVED, null));

        listener.handleStateMessage(topic, payload);

        verify(updateGameStateUseCase).updateState(
                argThat(id -> id.id().equals("from-topic")), any());
    }

    @Test
    void malformedTopicWithTooFewTokensThrowsAioob() {
        byte[] payload = MqttPayloadSerializer.serialize(new GameStatePayload("g1", GameMachineStatus.AVAILABLE, null));
        assertThatThrownBy(() -> listener.handleStateMessage("building/b1/state", payload))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void malformedPayloadThrowsWrappedException() {
        String topic = "building/b1/game/g1/state";
        assertThatThrownBy(() -> listener.handleStateMessage(topic, "{bad json".getBytes()))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(updateGameStateUseCase);
    }

    @Test
    void nullPayloadThrowsNpe() {
        String topic = "building/b1/game/g1/state";
        assertThatThrownBy(() -> listener.handleStateMessage(topic, null))
                .isInstanceOf(NullPointerException.class);
    }
}
