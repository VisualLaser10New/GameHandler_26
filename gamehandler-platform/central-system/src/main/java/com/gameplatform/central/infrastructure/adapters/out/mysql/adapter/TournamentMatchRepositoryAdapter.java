package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentMatchMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentMatchJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentMatchRepository} port (FASE 4 / C.8
 * scaffolding). Mirrors the {@code GameDefinitionRepositoryAdapter} /
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; writes carry the default {@code @Transactional}
 * propagation and reads are marked {@code @Transactional(readOnly = true)}.
 * All read paths are null-safe, returning {@code Optional.empty()} /
 * {@code List.of()} when their arguments are {@code null}.
 */
@Component
public class TournamentMatchRepositoryAdapter implements TournamentMatchRepository {

    private final TournamentMatchJpaRepository jpaRepo;
    private final TournamentMatchMapper mapper;

    public TournamentMatchRepositoryAdapter(TournamentMatchJpaRepository jpaRepo,
                                            TournamentMatchMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentMatch save(TournamentMatch match) {
        TournamentMatchJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(match));
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentMatch> findById(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatch> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentMatchJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(TournamentMatchId id) {
        if (id == null) {
            return;
        }
        jpaRepo.deleteById(id.value());
    }

    @Override
    @Transactional
    public Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition) {
        if (tournamentId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndRoundAndBracketPositionForUpdate(
                tournamentId.value(), round, bracketPosition).map(mapper::toDomain);
    }
}