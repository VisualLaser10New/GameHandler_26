package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentBuildingMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentBuildingJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA adapter for the {@link TournamentBuildingRepository} port. Mirrors the
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the JPA
 * repository + mapper and exposes the primitive {@code String} building-id API
 * (the backing {@code tournament_buildings} table is a pure join-table with no
 * central domain POJO of its own, per FASE 4 PIANO &sect;3.1). Writes carry the
 * default {@code @Transactional} propagation; reads are marked
 * {@code @Transactional(readOnly = true)} and are null-safe
 * ({@code List.of()} / {@code false} on {@code null} args).
 */
@Component
public class TournamentBuildingRepositoryAdapter implements TournamentBuildingRepository {

    private final TournamentBuildingJpaRepository jpaRepo;
    private final TournamentBuildingMapper mapper;

    public TournamentBuildingRepositoryAdapter(TournamentBuildingJpaRepository jpaRepo,
                                              TournamentBuildingMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void saveAll(TournamentId tournamentId, List<String> buildingIds) {
        if (tournamentId == null || buildingIds == null) {
            return;
        }
        for (String buildingId : buildingIds) {
            if (buildingId == null || buildingId.isBlank()) {
                continue;
            }
            jpaRepo.save(mapper.toEntity(tournamentId.value(), buildingId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentBuildingJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(TournamentBuildingJpaEntity::getBuildingId).toList();
    }

    @Override
    @Transactional
    public void deleteByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepo.deleteByTournamentId(tournamentId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndBuilding(TournamentId tournamentId, String buildingId) {
        if (tournamentId == null || buildingId == null) {
            return false;
        }
        return jpaRepo.existsByTournamentIdAndBuildingId(tournamentId.value(), buildingId);
    }
}