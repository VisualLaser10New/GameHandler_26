package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.SessionParticipantJpaEntity;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GameSessionMapper {
    private static final Logger log = LoggerFactory.getLogger(GameSessionMapper.class);
    private final ObjectMapper objectMapper;

    public GameSessionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GameSession toDomain(GameSessionJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        GameResult result = null;
        if (entity.getResultData() != null && !entity.getResultData().isBlank()) {
            try {
                result = objectMapper.readValue(entity.getResultData(), GameResult.class);
            } catch (JsonProcessingException e) {
                log.warn("Cannot deserialize result_data for session {}: {}", entity.getId(), e.getMessage());
            }
        }

        List<UserId> participants = new ArrayList<>();
        if (entity.getParticipants() != null) {
            participants = entity.getParticipants().stream()
                .map(p -> new UserId(p.getUserId()))
                .collect(Collectors.toList());
        }

        return new GameSession(
            new GameSessionId(entity.getId()),
            new GameId(entity.getGameId()),
            GameType.valueOf(entity.getGameType()),
            new BuildingId(entity.getBuildingId()),
            GameStatus.valueOf(entity.getStatus()),
            entity.getStartedAt(),
            entity.getEndedAt(),
            entity.getDurationSeconds(),
            entity.getWinnerId() != null ? new UserId(entity.getWinnerId()) : null,
            entity.getWinCondition() != null ? WinCondition.valueOf(entity.getWinCondition()) : null,
            result,
            participants
        );
    }

    public GameSessionJpaEntity toEntity(GameSession domain) {
        if (domain == null) {
            return null;
        }

        GameSessionJpaEntity entity = new GameSessionJpaEntity();
        entity.setId(domain.getId().value());
        entity.setGameId(domain.getGameId().id());
        entity.setGameType(domain.getGameType().name());
        entity.setBuildingId(domain.getBuildingId().id());
        entity.setStatus(domain.getStatus().name());
        entity.setStartedAt(domain.getStartedAt());
        entity.setEndedAt(domain.getEndedAt());
        entity.setDurationSeconds(domain.getDurationSeconds());
        entity.setWinnerId(domain.getWinnerId() != null ? domain.getWinnerId().value() : null);
        entity.setWinCondition(domain.getWinCondition() != null ? domain.getWinCondition().name() : null);

        String resultJson = null;
        if (domain.getResult() != null) {
            try {
                resultJson = objectMapper.writeValueAsString(domain.getResult());
            } catch (JsonProcessingException e) {
                log.error("Cannot serialize GameResult for session {}: {}", domain.getId().value(), e.getMessage());
            }
        }
        entity.setResultData(resultJson);

        List<SessionParticipantJpaEntity> participantEntities = new ArrayList<>();
        if (domain.getParticipants() != null) {
            participantEntities = domain.getParticipants().stream()
                .map(userId -> new SessionParticipantJpaEntity(domain.getId().value(), userId.value()))
                .collect(Collectors.toList());
        }
        entity.setParticipants(participantEntities);

        return entity;
    }
}
