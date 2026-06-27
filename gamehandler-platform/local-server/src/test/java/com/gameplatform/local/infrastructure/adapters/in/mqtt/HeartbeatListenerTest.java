package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SessionRecoveryService;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.mqtt.payload.HeartbeatAckPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@ExtendWith(MockitoExtension.class)
class HeartbeatListenerTest {

    @Mock private HealthCheckService healthCheckService;
    @Mock private SessionRecoveryService sessionRecoveryService;
    @Mock private PublishGameStatePort publishGameStatePort;
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private HeartbeatListener newListener() {
        return new HeartbeatListener(healthCheckService, sessionRecoveryService, publishGameStatePort, clock);
    }

    @Test
    void heartbeatRequestRegistersAndPublishesAck() {
        String topic = "building/b1/game/g1/heartbeat";
        newListener().handleHeartbeat(topic, new byte[0]);

        verify(healthCheckService).registerHeartbeat(new GameId("g1"));
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(publishGameStatePort).publishSessionEvent(eq("building/b1/game/g1/heartbeat/ack"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(HeartbeatAckPayload.class);
    }

    @Test
    void heartbeatAckRegistersAndDoesNotPublish() {
        String topic = "building/b1/game/g1/heartbeat/ack";
        newListener().handleHeartbeat(topic, new byte[0]);

        verify(healthCheckService).registerHeartbeat(new GameId("g1"));
        verify(sessionRecoveryService).registerHeartbeatAck(new GameId("g1"));
        verifyNoInteractions(publishGameStatePort);
    }

    @Test
    void malformedTopicThrowsAioob() {
        assertThatThrownBy(() -> newListener().handleHeartbeat("building/b1", new byte[0]))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }
}
