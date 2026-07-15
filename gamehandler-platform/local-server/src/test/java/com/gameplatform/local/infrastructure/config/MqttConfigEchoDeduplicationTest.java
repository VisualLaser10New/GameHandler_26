package com.gameplatform.local.infrastructure.config;

import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.ports.in.CancelLobbyUseCase;
import com.gameplatform.local.domain.ports.in.CreateLobbyUseCase;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.JoinLobbyUseCase;
import com.gameplatform.local.domain.ports.in.LeaveLobbyUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartLobbyUseCase;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameSessionListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameStateListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.HeartbeatListener;
import com.gameplatform.local.infrastructure.adapters.out.mqtt.OutboundMessageDeduplicationCache;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import com.gameplatform.shared.mqtt.payload.LobbyCreatePayload;
import com.gameplatform.shared.domain.model.GameType;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.List;

class MqttConfigEchoDeduplicationTest {

    @Test
    void sessionEchoInboundMatchingRecentOutboundIsDropped_andCreateLobbyUseCaseNotInvoked() throws Exception {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();

        StartGameSessionUseCase startGameSessionUseCase = mock(StartGameSessionUseCase.class);
        EndGameSessionUseCase endGameSessionUseCase = mock(EndGameSessionUseCase.class);
        PauseGameSessionUseCase pauseGameSessionUseCase = mock(PauseGameSessionUseCase.class);
        ResumeGameSessionUseCase resumeGameSessionUseCase = mock(ResumeGameSessionUseCase.class);
        CreateLobbyUseCase createLobbyUseCase = mock(CreateLobbyUseCase.class);
        JoinLobbyUseCase joinLobbyUseCase = mock(JoinLobbyUseCase.class);
        StartLobbyUseCase startLobbyUseCase = mock(StartLobbyUseCase.class);
        CancelLobbyUseCase cancelLobbyUseCase = mock(CancelLobbyUseCase.class);
        LeaveLobbyUseCase leaveLobbyUseCase = mock(LeaveLobbyUseCase.class);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        GameSessionListener gameSessionListener = new GameSessionListener(
                startGameSessionUseCase, endGameSessionUseCase, pauseGameSessionUseCase, resumeGameSessionUseCase,
                createLobbyUseCase, joinLobbyUseCase, startLobbyUseCase, cancelLobbyUseCase, leaveLobbyUseCase, objectMapper);

        GameStateListener gameStateListener = mock(GameStateListener.class);
        HeartbeatListener heartbeatListener = mock(HeartbeatListener.class);

        MqttConfig mqttConfig = new MqttConfig(mock(org.springframework.core.io.ResourceLoader.class), cache);
        setField(mqttConfig, "brokerUrl", "tcp://localhost:1883");
        setField(mqttConfig, "buildingId", "test-building");
        setField(mqttConfig, "username", "");
        setField(mqttConfig, "password", "");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class)) {
            IMqttClient client = mqttConfig.mqttClient(gameStateListener, gameSessionListener, heartbeatListener);
            MqttClient mockClient = mockedClient.constructed().get(0);

            ArgumentCaptor<MqttCallbackExtended> cbCaptor = ArgumentCaptor.forClass(MqttCallbackExtended.class);
            verify(mockClient).setCallback(cbCaptor.capture());
            cbCaptor.getValue().connectComplete(false, "tcp://localhost:1883");

            ArgumentCaptor<IMqttMessageListener> sessionListenerCaptor = ArgumentCaptor.forClass(IMqttMessageListener.class);
            verify(mockClient).subscribe(eq("building/test-building/game/+/session/#"), eq(1), sessionListenerCaptor.capture());
            IMqttMessageListener lambda = sessionListenerCaptor.getValue();

            String topic = "building/test-building/game/g1/session/lobby/create";
            byte[] bytes = MqttPayloadSerializer.serialize(
                    new SessionStartPayload("s1", GameType.CHESS, List.of("u1")));

            cache.recordOutbound(topic, bytes);

            lambda.messageArrived(topic, new MqttMessage(bytes));

            verifyNoInteractions(createLobbyUseCase);
            verifyNoInteractions(startGameSessionUseCase);
        }
    }

    @Test
    void sessionInboundNotInCacheIsForwardedToListener() throws Exception {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();

        StartGameSessionUseCase startGameSessionUseCase = mock(StartGameSessionUseCase.class);
        EndGameSessionUseCase endGameSessionUseCase = mock(EndGameSessionUseCase.class);
        PauseGameSessionUseCase pauseGameSessionUseCase = mock(PauseGameSessionUseCase.class);
        ResumeGameSessionUseCase resumeGameSessionUseCase = mock(ResumeGameSessionUseCase.class);
        CreateLobbyUseCase createLobbyUseCase = mock(CreateLobbyUseCase.class);
        JoinLobbyUseCase joinLobbyUseCase = mock(JoinLobbyUseCase.class);
        StartLobbyUseCase startLobbyUseCase = mock(StartLobbyUseCase.class);
        CancelLobbyUseCase cancelLobbyUseCase = mock(CancelLobbyUseCase.class);
        LeaveLobbyUseCase leaveLobbyUseCase = mock(LeaveLobbyUseCase.class);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        GameSessionListener gameSessionListener = new GameSessionListener(
                startGameSessionUseCase, endGameSessionUseCase, pauseGameSessionUseCase, resumeGameSessionUseCase,
                createLobbyUseCase, joinLobbyUseCase, startLobbyUseCase, cancelLobbyUseCase, leaveLobbyUseCase, objectMapper);

        GameStateListener gameStateListener = mock(GameStateListener.class);
        HeartbeatListener heartbeatListener = mock(HeartbeatListener.class);

        MqttConfig mqttConfig = new MqttConfig(mock(org.springframework.core.io.ResourceLoader.class), cache);
        setField(mqttConfig, "brokerUrl", "tcp://localhost:1883");
        setField(mqttConfig, "buildingId", "test-building");
        setField(mqttConfig, "username", "");
        setField(mqttConfig, "password", "");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class)) {
            IMqttClient client = mqttConfig.mqttClient(gameStateListener, gameSessionListener, heartbeatListener);
            MqttClient mockClient = mockedClient.constructed().get(0);

            ArgumentCaptor<MqttCallbackExtended> cbCaptor = ArgumentCaptor.forClass(MqttCallbackExtended.class);
            verify(mockClient).setCallback(cbCaptor.capture());
            cbCaptor.getValue().connectComplete(false, "tcp://localhost:1883");

            ArgumentCaptor<IMqttMessageListener> sessionListenerCaptor = ArgumentCaptor.forClass(IMqttMessageListener.class);
            verify(mockClient).subscribe(eq("building/test-building/game/+/session/#"), eq(1), sessionListenerCaptor.capture());
            IMqttMessageListener lambda = sessionListenerCaptor.getValue();

            String topic = "building/test-building/game/g1/session/lobby/create";
            byte[] bytes = MqttPayloadSerializer.serialize(
                    new LobbyCreatePayload(GameType.CHESS, "creator-1"));

            lambda.messageArrived(topic, new MqttMessage(bytes));

            verify(createLobbyUseCase).createLobby(
                    eq(new com.gameplatform.shared.domain.model.GameId("g1")),
                    eq(GameType.CHESS),
                    eq(new com.gameplatform.shared.domain.model.UserId("creator-1")));
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
