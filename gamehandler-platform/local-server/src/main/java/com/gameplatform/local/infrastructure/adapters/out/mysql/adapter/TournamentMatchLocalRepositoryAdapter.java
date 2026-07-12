package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentMatchLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentMatchLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementing {@link TournamentMatchLocalRepository}. {@code save} is
 * an idempotent upsert by PK {@code id} (mirror of
 * {@code GameDefinitionLocalRepositoryAdapter}).
 */
@Component
public class TournamentMatchLocalRepositoryAdapter implements TournamentMatchLocalRepository {

    private final TournamentMatchLocalJpaRepository jpaRepository;
    private final TournamentMatchLocalMapper mapper;

    public TournamentMatchLocalRepositoryAdapter(TournamentMatchLocalJpaRepository jpaRepository,
                                                 TournamentMatchLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentMatchLocal save(TournamentMatchLocal match) {
        if (match == null) {
            return null;
        }
        TournamentMatchLocalJpaEntity entity = mapper.toEntity(match);
        TournamentMatchLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentMatchLocal> findById(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentMatchLocalJpaEntity> entities = jpaRepository.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findScheduledByParticipant(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<TournamentMatchLocalJpaEntity> entities =
                jpaRepository.findByParticipantAndStatus(userId, TournamentMatchStatus.SCHEDULED.name());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(TournamentMatchId id) {
        if (id == null) {
            return;
        }
        jpaRepository.deleteById(id.value());
    }
}