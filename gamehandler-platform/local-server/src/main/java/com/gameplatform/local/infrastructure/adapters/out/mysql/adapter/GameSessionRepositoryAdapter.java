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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GameSessionRepositoryAdapter implements GameSessionRepository {

    private final GameSessionJpaRepository jpaRepository;
    private final GameSessionMapper mapper;

    public GameSessionRepositoryAdapter(GameSessionJpaRepository jpaRepository, GameSessionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public GameSession save(GameSession session) {
        GameSessionJpaEntity entity = mapper.toEntity(session);
        GameSessionJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
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
        return List.of();
    }

    @Override
    public Optional<GameSession> findActiveByGameId(GameId gameId) {
        return jpaRepository.findFirstByGameIdAndStatusIn(
            gameId.id(),
            List.of("WAITING", "IN_PROGRESS", "PAUSED")
        ).map(mapper::toDomain);
    }
}
