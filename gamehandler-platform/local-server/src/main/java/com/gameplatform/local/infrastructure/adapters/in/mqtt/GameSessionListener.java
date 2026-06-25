package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.SessionEndPayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameSessionListener {

    private final StartGameSessionUseCase startGameSessionUseCase;
    private final EndGameSessionUseCase endGameSessionUseCase;
    private final PauseGameSessionUseCase pauseGameSessionUseCase;
    private final ResumeGameSessionUseCase resumeGameSessionUseCase;
    private final ObjectMapper objectMapper;

    public GameSessionListener(
            StartGameSessionUseCase startGameSessionUseCase,
            EndGameSessionUseCase endGameSessionUseCase,
            PauseGameSessionUseCase pauseGameSessionUseCase,
            ResumeGameSessionUseCase resumeGameSessionUseCase,
            ObjectMapper objectMapper) {
        this.startGameSessionUseCase = startGameSessionUseCase;
        this.endGameSessionUseCase = endGameSessionUseCase;
        this.pauseGameSessionUseCase = pauseGameSessionUseCase;
        this.resumeGameSessionUseCase = resumeGameSessionUseCase;
        this.objectMapper = objectMapper;
    }

    public void handleSessionMessage(String topic, byte[] payload) {
        String[] tokens = topic.split("/");
        String gameId = tokens[3];
        String action = tokens[5];

        switch (action) {
            case "start" -> {
                SessionStartPayload startPayload = MqttPayloadSerializer.deserialize(payload, SessionStartPayload.class);
                List<UserId> participants = startPayload.participants() != null
                        ? startPayload.participants().stream().map(UserId::new).toList()
                        : List.of();
                startGameSessionUseCase.start(new GameId(gameId), startPayload.gameType(), participants, null);
            }
            case "end" -> {
                SessionEndPayload endPayload = MqttPayloadSerializer.deserialize(payload, SessionEndPayload.class);
                GameResult result = null;
                if (endPayload.resultData() != null && !endPayload.resultData().isBlank()) {
                    try {
                        result = objectMapper.readValue(endPayload.resultData(), GameResult.class);
                    } catch (Exception e) {
                        // Fallback mapping on parsing exception
                    }
                }
                if (result == null) {
                    final String winnerIdVal = endPayload.winnerId();
                    final WinCondition winConditionVal = endPayload.winCondition();
                    result = new GameResult() {
                        @Override
                        public UserId getWinnerId() {
                            return winnerIdVal != null ? new UserId(winnerIdVal) : null;
                        }
                        @Override
                        public List<UserId> getWinnerIds() {
                            return winnerIdVal != null ? List.of(new UserId(winnerIdVal)) : List.of();
                        }
                        @Override
                        public WinCondition getWinCondition() {
                            return winConditionVal;
                        }
                    };
                }
                endGameSessionUseCase.end(new GameSessionId(endPayload.sessionId()), result);
            }
            case "pause" -> {
                SessionPausePayload pausePayload = MqttPayloadSerializer.deserialize(payload, SessionPausePayload.class);
                pauseGameSessionUseCase.pause(new GameSessionId(pausePayload.sessionId()));
            }
            case "resume" -> {
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
                    String sessionId = node.get("sessionId").asText();
                    resumeGameSessionUseCase.resume(new GameSessionId(sessionId));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse session ID from resume payload", e);
                }
            }
        }
    }
}
