package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentStandingMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentStandingJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentStandingRepository} port (FASE 4 / C.8
 * scaffolding). Mirrors the {@code GameDefinitionRepositoryAdapter} /
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; writes carry the default {@code @Transactional}
 * propagation and reads are marked {@code @Transactional(readOnly = true)}.
 * All read paths are null-safe, returning {@code Optional.empty()} /
 * {@code List.of()} when their arguments are {@code null}.
 */
@Component
public class TournamentStandingRepositoryAdapter implements TournamentStandingRepository {

    private final TournamentStandingJpaRepository jpaRepo;
    private final TournamentStandingMapper mapper;

    public TournamentStandingRepositoryAdapter(TournamentStandingJpaRepository jpaRepo,
                                               TournamentStandingMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentStanding save(TournamentStanding standing) {
        TournamentStandingJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(standing));
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentStanding> findByTournamentAndParticipantId(TournamentId tournamentId,
                                                                        String participantId) {
        if (tournamentId == null || participantId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndParticipantId(tournamentId.value(), participantId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentStanding> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentStandingJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return;
        }
        jpaRepo.deleteByTournamentAndParticipantId(tournamentId.value(), participantId);
    }

    @Override
    @Transactional
    public List<TournamentStanding> findByTournamentIdForUpdate(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentStandingJpaEntity> entities =
                jpaRepo.findByTournamentIdForUpdate(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }
}