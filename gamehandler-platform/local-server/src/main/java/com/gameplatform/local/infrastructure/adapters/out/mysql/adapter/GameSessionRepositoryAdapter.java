package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameSessionMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameSessionJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import java.util.Objects;
import java.util.Set;

@Component
public class GameSessionRepositoryAdapter implements GameSessionRepository {

    private final GameSessionJpaRepository jpaRepository;
    private final GameSessionMapper mapper;
    private final OutboxEventJpaRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public GameSessionRepositoryAdapter(GameSessionJpaRepository jpaRepository, GameSessionMapper mapper, OutboxEventJpaRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public GameSession save(GameSession session) {
        GameSessionJpaEntity entity = mapper.toEntity(session);
        try {
            GameSessionJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new com.gameplatform.local.domain.exception.ConcurrentStateException(
                "Concurrent modification of game session " + session.getId().value(), ex);
        }
    }

    @Override
    public Optional<GameSession> findById(GameSessionId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<GameSession> findByBuildingId(BuildingId buildingId) {
        return jpaRepository.findByBuildingId(buildingId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<GameSession> findByGameType(GameType gameType) {
        return jpaRepository.findByGameType(gameType.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<GameSession> findByStatus(GameStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<GameSession> findPendingSync() {
        List<GameSessionJpaEntity> completedOrAbortedSessions = jpaRepository.findByStatusIn(List.of("COMPLETED", "ABORTED"));
        List<OutboxEventJpaEntity> sentEvents = outboxEventRepository.findByEventTypeAndStatus("GAME_SESSION_COMPLETED", "SENT");
        
        Set<String> sentSessionIds = sentEvents.stream()
            .map(event -> {
                try {
                    JsonNode node = objectMapper.readTree(event.getPayload());
                    JsonNode sessionIdNode = node.get("sessionId");
                    return sessionIdNode != null ? sessionIdNode.asText() : null;
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return completedOrAbortedSessions.stream()
            .filter(session -> !sentSessionIds.contains(session.getId()))
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<GameSession> findActiveByGameId(GameId gameId) {
        return jpaRepository.findFirstByGameIdAndStatusIn(
            gameId.id(),
            List.of("WAITING", "IN_PROGRESS", "PAUSED")
        ).map(mapper::toDomain);
    }

    @Override
    public List<GameSession> findByParticipant(UserId userId) {
        if (userId == null) {
            return List.of();
        }
        return jpaRepository.findByParticipantUserId(userId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
