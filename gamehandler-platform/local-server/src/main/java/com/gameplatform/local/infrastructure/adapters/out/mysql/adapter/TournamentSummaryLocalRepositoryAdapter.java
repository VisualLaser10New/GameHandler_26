package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentSummaryLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentSummaryLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentSummaryLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link TournamentSummaryLocalRepository} port. Matches
 * the LOCAL FASE 6 {@code TournamentMatchLocalRepositoryAdapter} shape:
 * constructor-injects the JPA repository + mapper; {@code save} is an upsert
 * by PK {@code tournament_id} (the underlying
 * {@link TournamentSummaryLocalJpaRepository#save} merges an existing row if
 * the {@code tournament_id} PK is already present — idempotent on
 * re-application of the same summary snapshot).
 */
@Component
public class TournamentSummaryLocalRepositoryAdapter implements TournamentSummaryLocalRepository {

    private final TournamentSummaryLocalJpaRepository jpaRepository;
    private final TournamentSummaryLocalMapper mapper;

    public TournamentSummaryLocalRepositoryAdapter(TournamentSummaryLocalJpaRepository jpaRepository,
                                                   TournamentSummaryLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentSummaryLocal save(TournamentSummaryLocal summary) {
        if (summary == null) {
            return null;
        }
        TournamentSummaryLocalJpaEntity entity = mapper.toEntity(summary);
        TournamentSummaryLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentSummaryLocal> findById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(tournamentId.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentSummaryLocal> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepository.deleteById(tournamentId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TournamentId tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        return jpaRepository.existsById(tournamentId.value());
    }
}
