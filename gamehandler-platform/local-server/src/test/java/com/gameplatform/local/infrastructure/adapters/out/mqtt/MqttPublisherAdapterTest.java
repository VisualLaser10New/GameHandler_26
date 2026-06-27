package com.gameplatform.local.infrastructure.adapters.out.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.mqtt.payload.AlertPayload;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MqttPublisherAdapterTest {

    @Mock private IMqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MqttPublisherAdapter adapter;

    @BeforeEach
    void setup() {
        adapter = new MqttPublisherAdapter(mqttClient, objectMapper, "b1");
    }

    @Test
    void publishStateSendsToGameStateTopicWithQos1RetainedTrue() throws Exception {
        adapter.publishState(new GameId("g1"), GameMachineStatus.IN_USE);

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> msgCap = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(topicCap.capture(), msgCap.capture());
        assertThat(topicCap.getValue()).isEqualTo("building/b1/game/g1/state");
        assertThat(msgCap.getValue().getQos()).isEqualTo(1);
        assertThat(msgCap.getValue().isRetained()).isTrue();
    }

    @Test
    void publishAlertSendsToAlertsTopic() throws Exception {
        adapter.publishAlert(new AlertPayload("UNREACHABLE", "g1", "msg", Instant.now()));
        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        verify(mqttClient).publish(topicCap.capture(), any());
        assertThat(topicCap.getValue()).isEqualTo("building/b1/alerts");
    }

    @Test
    void publishSessionEventStartBuildsStartPayload() throws Exception {
        GameSession session = new GameSession(new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId("b1"), GameStatus.IN_PROGRESS, Instant.parse("2026-02-01T10:00:00Z"),
                null, null, null, null, null, List.of(new UserId("u1")));
        adapter.publishSessionEvent("building/b1/game/g1/session/start", session);

        ArgumentCaptor<MqttMessage> msgCap = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(eq("building/b1/game/g1/session/start"), msgCap.capture());
        assertThat(msgCap.getValue().getQos()).isEqualTo(1);
        assertThat(msgCap.getValue().isRetained()).isFalse();
        String body = new String(msgCap.getValue().getPayload());
        assertThat(body).contains("\"sessionId\":\"s1\"").contains("\"gameType\":\"CHESS\"").contains("\"u1\"");
    }

    @Test
    void publishSessionEventEndBuildsEndPayloadWithResultJson() throws Exception {
        GameResult result = new GameResult() {
            @Override public UserId getWinnerId() { return new UserId("winner"); }
            @Override public List<UserId> getWinnerIds() { return List.of(new UserId("winner")); }
            @Override public WinCondition getWinCondition() { return WinCondition.WIN; }
        };
        GameSession session = new GameSession(new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId("b1"), GameStatus.COMPLETED, Instant.parse("2026-02-01T10:00:00Z"),
                Instant.parse("2026-02-01T11:00:00Z"), 3600, new UserId("winner"), WinCondition.WIN, result, List.of());

        adapter.publishSessionEvent("building/b1/game/g1/session/end", session);
        ArgumentCaptor<MqttMessage> msgCap = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(eq("building/b1/game/g1/session/end"), msgCap.capture());
        String body = new String(msgCap.getValue().getPayload());
        assertThat(body).contains("\"winnerId\":\"winner\"").contains("\"winCondition\":\"WIN\"");
    }

    @Test
    void publishStateSwallowsMqttExceptionAndDoesNotPropagate() throws Exception {
        doThrow(new MqttException(new Throwable())).when(mqttClient).publish(anyString(), any());
        adapter.publishState(new GameId("g1"), GameMachineStatus.AVAILABLE);
        verify(mqttClient).publish(anyString(), any());
    }

    @Test
    void publishSessionEventWithNonGameSessionPayloadSerializesDirectly() throws Exception {
        adapter.publishSessionEvent("any/topic", "a-string-payload");
        ArgumentCaptor<MqttMessage> msgCap = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(eq("any/topic"), msgCap.capture());
        assertThat(new String(msgCap.getValue().getPayload())).contains("a-string-payload");
    }
}