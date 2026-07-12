package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape: constructor-injects the JPA repository + mapper; writes carry the default
 * {@code @Transactional} propagation and reads are marked
 * {@code @Transactional(readOnly = true)}. All read paths are null-safe, returning
 * {@code Optional.empty()} / {@code List.of()} / {@code false} when their arguments
 * are {@code null}.
 */
@Component
public class TournamentRepositoryAdapter implements TournamentRepository {

    private final TournamentJpaRepository jpaRepo;
    private final TournamentMapper mapper;

    public TournamentRepositoryAdapter(TournamentJpaRepository jpaRepo, TournamentMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Tournament save(Tournament tournament) {
        TournamentJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(tournament));
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tournament> findById(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tournament> findAll() {
        List<TournamentJpaEntity> entities = jpaRepo.findAllByOrderByCreatedAtDesc();
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tournament> findByStatus(TournamentStatus status) {
        if (status == null) {
            return List.of();
        }
        List<TournamentJpaEntity> entities = jpaRepo.findByStatus(status.name());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TournamentId id) {
        if (id == null) {
            return false;
        }
        return jpaRepo.existsById(id.value());
    }

    @Override
    @Transactional
    public void deleteById(TournamentId id) {
        if (id == null) {
            return;
        }
        jpaRepo.deleteById(id.value());
    }

    @Override
    @Transactional
    public Optional<Tournament> findByIdForUpdate(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }
}