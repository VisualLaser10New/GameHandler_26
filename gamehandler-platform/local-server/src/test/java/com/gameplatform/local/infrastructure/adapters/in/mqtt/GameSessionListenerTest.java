package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.SessionEndPayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionResumePayload;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class GameSessionListenerTest {

    @Mock private StartGameSessionUseCase startGameSessionUseCase;
    @Mock private EndGameSessionUseCase endGameSessionUseCase;
    @Mock private PauseGameSessionUseCase pauseGameSessionUseCase;
    @Mock private ResumeGameSessionUseCase resumeGameSessionUseCase;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private GameSessionListener listener;

    private String startTopic() { return "building/b1/game/g1/session/start"; }
    private String endTopic() { return "building/b1/game/g1/session/end"; }
    private String pauseTopic() { return "building/b1/game/g1/session/pause"; }
    private String resumeTopic() { return "building/b1/game/g1/session/resume"; }

    @Test
    void startDeserializesAndCallsStartWithNullReservation() {
        byte[] payload = MqttPayloadSerializer.serialize(
                new SessionStartPayload("s1", GameType.CHESS, List.of("u1")));
        listener.handleSessionMessage(startTopic(), payload);
        verify(startGameSessionUseCase).start(eq(new com.gameplatform.shared.domain.model.GameId("g1")),
                eq(GameType.CHESS),
                argThat(list -> list.size() == 1 && list.get(0).equals(new UserId("u1"))),
                isNull());
    }

    @Test
    void startWithNullParticipantsSendsEmptyList() {
        byte[] payload = MqttPayloadSerializer.serialize(
                new SessionStartPayload("s1", GameType.FOOSBALL, null));
        listener.handleSessionMessage(startTopic(), payload);
        verify(startGameSessionUseCase).start(any(), eq(GameType.FOOSBALL),
                argThat(List::isEmpty), isNull());
    }

    @Test
    void pauseDeserializesAndCallsPause() {
        byte[] payload = MqttPayloadSerializer.serialize(new SessionPausePayload("s1", "u1"));
        listener.handleSessionMessage(pauseTopic(), payload);
        verify(pauseGameSessionUseCase).pause(eq(new com.gameplatform.shared.domain.model.GameSessionId("s1")));
    }

    @Test
    void resumeDeserializesAndCallsResume() {
        byte[] payload = MqttPayloadSerializer.serialize(new SessionResumePayload("s-resume"));
        listener.handleSessionMessage(resumeTopic(), payload);
        verify(resumeGameSessionUseCase).resume(eq(new com.gameplatform.shared.domain.model.GameSessionId("s-resume")));
    }

    @Test
    void resumeWithMissingSessionIdIsNoOpAndDoesNotCallResume() {
        byte[] payload = MqttPayloadSerializer.serialize(new SessionResumePayload(null));
        assertThatCode(() -> listener.handleSessionMessage(resumeTopic(), payload))
                .doesNotThrowAnyException();
        verify(resumeGameSessionUseCase, never()).resume(any());
    }

    @Test
    void endWithFallbackResultWhenResultDataBlank() {
        byte[] payload = MqttPayloadSerializer.serialize(
                new SessionEndPayload("s1", "winner-1",
                        com.gameplatform.shared.domain.model.WinCondition.WIN, ""));
        listener.handleSessionMessage(endTopic(), payload);
        verify(endGameSessionUseCase).end(eq(new com.gameplatform.shared.domain.model.GameSessionId("s1")),
                argThat(r -> r.getWinnerId() != null
                        && r.getWinnerId().equals(new UserId("winner-1"))
                        && r.getWinCondition() == com.gameplatform.shared.domain.model.WinCondition.WIN));
    }

    @Test
    void endWithNullWinnerBuildsResultWithNullWinner() {
        byte[] payload = MqttPayloadSerializer.serialize(
                new SessionEndPayload("s1", null,
                        com.gameplatform.shared.domain.model.WinCondition.ABANDONED, null));
        listener.handleSessionMessage(endTopic(), payload);
        verify(endGameSessionUseCase).end(any(),
                argThat(r -> r.getWinnerId() == null
                        && r.getWinCondition() == com.gameplatform.shared.domain.model.WinCondition.ABANDONED));
    }

    @Test
    void malformedTopicThrowsAioob() {
        byte[] payload = MqttPayloadSerializer.serialize(
                new SessionStartPayload("s1", GameType.CHESS, List.of()));
        assertThatThrownBy(() -> listener.handleSessionMessage("building/b1/game/g1/session", payload))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void unknownActionIsSilentlyIgnored() {
        String topic = "building/b1/game/g1/session/unknown";
        byte[] payload = "{}".getBytes();
        listener.handleSessionMessage(topic, payload);
        verifyNoInteractions(startGameSessionUseCase, endGameSessionUseCase,
                pauseGameSessionUseCase, resumeGameSessionUseCase);
    }
}
