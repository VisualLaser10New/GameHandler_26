package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.ports.out.GameDefinitionRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.GameDefinitionJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.GameDefinitionMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.GameDefinitionJpaRepository;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link GameDefinitionRepository} port. Matches the
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; {@code save} is an upsert by the business PK
 * {@code game_type} (the underlying {@link GameDefinitionJpaRepository#save}
 * merges an existing row if the game_type PK is already present).
 */
@Component
public class GameDefinitionRepositoryAdapter implements GameDefinitionRepository {

    private final GameDefinitionJpaRepository jpaRepository;
    private final GameDefinitionMapper mapper;

    public GameDefinitionRepositoryAdapter(GameDefinitionJpaRepository jpaRepository,
                                           GameDefinitionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public GameDefinition save(GameDefinition gameDefinition) {
        GameDefinitionJpaEntity entity = mapper.toEntity(gameDefinition);
        GameDefinitionJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GameDefinition> findByGameType(GameType gameType) {
        if (gameType == null) {
            return Optional.empty();
        }
        return jpaRepository.findByGameType(gameType.name()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameDefinition> findAll() {
        return jpaRepository.findAllByOrderByGameTypeAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByGameType(GameType gameType) {
        if (gameType == null) {
            return false;
        }
        return jpaRepository.existsByGameType(gameType.name());
    }

    @Override
    @Transactional
    public void deleteByGameType(GameType gameType) {
        if (gameType == null) {
            return;
        }
        jpaRepository.deleteByGameType(gameType.name());
    }
}
