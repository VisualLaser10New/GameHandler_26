package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.domain.ports.out.TournamentStandingsLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentStandingLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentStandingLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link TournamentStandingsLocalRepository} port
 * (PIANO §7.B). {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code participantId});
 * {@code deleteByTournament} removes the full per-tournament snapshot in
 * one bulk delete — used by the sync service's full-snapshot replace
 * (delete+insert idempotency).
 */
@Component
public class TournamentStandingLocalRepositoryAdapter implements TournamentStandingsLocalRepository {

    private final TournamentStandingLocalJpaRepository jpaRepository;
    private final TournamentStandingLocalMapper mapper;

    public TournamentStandingLocalRepositoryAdapter(TournamentStandingLocalJpaRepository jpaRepository,
                                                     TournamentStandingLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentStandingLocal save(TournamentStandingLocal standing) {
        if (standing == null) {
            return null;
        }
        TournamentStandingLocalJpaEntity entity = mapper.toEntity(standing);
        TournamentStandingLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentStandingLocal> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        return jpaRepository.findByTournamentId(tournamentId.value()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepository.deleteByTournamentId(tournamentId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null || participantId.isBlank()) {
            return false;
        }
        return jpaRepository.existsByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}