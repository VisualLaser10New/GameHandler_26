package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GameRepositoryAdapter implements GameRepository {

    private final GameJpaRepository jpaRepository;
    private final GameMapper mapper;

    public GameRepositoryAdapter(GameJpaRepository jpaRepository, GameMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Game save(Game game) {
        GameJpaEntity entity = mapper.toEntity(game);
        GameJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Game> findById(GameId id) {
        return jpaRepository.findById(id.id()).map(mapper::toDomain);
    }

    @Override
    public Optional<Game> findByIdForUpdate(GameId id) {
        return jpaRepository.findByIdForUpdate(id.id()).map(mapper::toDomain);
    }

    @Override
    public List<Game> findByBuildingId(BuildingId buildingId) {
        return jpaRepository.findByBuildingId(buildingId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Game> findByStatus(GameMachineStatus status) {
        return jpaRepository.findByStatus(status).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Game> findAll() {
        return jpaRepository.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
