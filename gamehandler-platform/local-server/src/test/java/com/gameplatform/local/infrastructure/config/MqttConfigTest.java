package com.gameplatform.local.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameSessionListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameStateListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.HeartbeatListener;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;

class MqttConfigTest {

    @Test
    void testMqttClientConfigurationAndSubscriptions() throws Exception {
        // Arrange
        GameStateListener gameStateListener = mock(GameStateListener.class);
        GameSessionListener gameSessionListener = mock(GameSessionListener.class);
        HeartbeatListener heartbeatListener = mock(HeartbeatListener.class);

        MqttConfig mqttConfig = new MqttConfig(mock(org.springframework.core.io.ResourceLoader.class));

        // Inject private properties using reflection
        setField(mqttConfig, "brokerUrl", "tcp://localhost:1883");
        setField(mqttConfig, "buildingId", "test-building");
        setField(mqttConfig, "username", "user");
        setField(mqttConfig, "password", "pass");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class)) {
            // Act
            IMqttClient client = mqttConfig.mqttClient(gameStateListener, gameSessionListener, heartbeatListener);

            // Assert
            assertThat(client).isNotNull();
            assertThat(mockedClient.constructed()).hasSize(1);
            MqttClient mockClientInstance = mockedClient.constructed().get(0);

            // Verify callback is set
            ArgumentCaptor<MqttCallbackExtended> callbackCaptor = ArgumentCaptor.forClass(MqttCallbackExtended.class);
            verify(mockClientInstance).setCallback(callbackCaptor.capture());
            MqttCallbackExtended callback = callbackCaptor.getValue();
            assertThat(callback).isNotNull();

            // Verify connect is called with expected options
            ArgumentCaptor<MqttConnectOptions> optionsCaptor = ArgumentCaptor.forClass(MqttConnectOptions.class);
            verify(mockClientInstance).connect(optionsCaptor.capture());
            MqttConnectOptions options = optionsCaptor.getValue();
            assertThat(options.isAutomaticReconnect()).isTrue();
            assertThat(options.isCleanSession()).isTrue();
            assertThat(options.getUserName()).isEqualTo("user");
            assertThat(new String(options.getPassword())).isEqualTo("pass");

            // Verify subscriptions are NOT made yet before connectComplete is called
            verify(mockClientInstance, never()).subscribe(anyString(), anyInt(), any());

            // Act - Trigger connectComplete callback (both initial and reconnect scenarios)
            callback.connectComplete(false, "tcp://localhost:1883");

            // Assert subscriptions are now made
            verify(mockClientInstance).subscribe(eq("building/test-building/game/+/state"), eq(1), any());
            verify(mockClientInstance).subscribe(eq("building/test-building/game/+/session/+"), eq(1), any());
            verify(mockClientInstance).subscribe(eq("building/test-building/game/+/heartbeat"), eq(0), any());
            verify(mockClientInstance).subscribe(eq("building/test-building/game/+/heartbeat/ack"), eq(0), any());
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
