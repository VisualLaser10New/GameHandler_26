package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.RegisteredLocalServerLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.RegisteredLocalServerLocalJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link RegisteredLocalServerLocalRepository} port
 * (PIANO §7.B). {@code save} is an upsert by PK {@code buildingId}.
 */
@Component
public class RegisteredLocalServerLocalRepositoryAdapter implements RegisteredLocalServerLocalRepository {

    private final RegisteredLocalServerLocalJpaRepository jpaRepository;
    private final RegisteredLocalServerLocalMapper mapper;

    public RegisteredLocalServerLocalRepositoryAdapter(RegisteredLocalServerLocalJpaRepository jpaRepository,
                                                        RegisteredLocalServerLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RegisteredLocalServerLocal save(RegisteredLocalServerLocal server) {
        if (server == null) {
            return null;
        }
        RegisteredLocalServerLocalJpaEntity entity = mapper.toEntity(server);
        RegisteredLocalServerLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegisteredLocalServerLocal> findById(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(buildingId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegisteredLocalServerLocal> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return;
        }
        jpaRepository.deleteById(buildingId);
    }
}