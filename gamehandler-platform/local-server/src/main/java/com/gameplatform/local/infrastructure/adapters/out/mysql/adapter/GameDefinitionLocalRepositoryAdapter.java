package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameDefinitionLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.GameDefinitionLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameDefinitionLocalJpaRepository;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link GameDefinitionLocalRepository} port. Matches the
 * LOCAL FASE 1 {@code LocalAdminBuildingLocalRepositoryAdapter} shape: constructor-
 * injects the JPA repository + mapper; {@code save} is an upsert by PK
 * {@code game_type} (the underlying {@link GameDefinitionLocalJpaRepository#save}
 * merges an existing row if the {@code game_type} PK is already present -
 * idempotent on re-application of the same game-definition snapshot).
 */
@Component
public class GameDefinitionLocalRepositoryAdapter implements GameDefinitionLocalRepository {

    private final GameDefinitionLocalJpaRepository jpaRepository;
    private final GameDefinitionLocalMapper mapper;

    public GameDefinitionLocalRepositoryAdapter(GameDefinitionLocalJpaRepository jpaRepository,
                                                 GameDefinitionLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public GameDefinitionLocal save(GameDefinitionLocal gameDefinitionLocal) {
        GameDefinitionLocalJpaEntity entity = mapper.toEntity(gameDefinitionLocal);
        GameDefinitionLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GameDefinitionLocal> findByGameType(GameType gameType) {
        if (gameType == null) {
            return Optional.empty();
        }
        return jpaRepository.findByGameType(gameType.name()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameDefinitionLocal> findAll() {
        return jpaRepository.findAllByOrderByGameTypeAsc().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
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