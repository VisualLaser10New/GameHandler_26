package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.domain.ports.out.TournamentParticipantsLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentParticipantLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentParticipantLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link TournamentParticipantsLocalRepository} port
 * (PIANO §7.B). {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code participantId});
 * {@code deleteByTournament} removes the full per-tournament snapshot in
 * one bulk delete — used by the sync service's full-snapshot replace
 * (delete+insert idempotency).
 */
@Component
public class TournamentParticipantLocalRepositoryAdapter implements TournamentParticipantsLocalRepository {

    private final TournamentParticipantLocalJpaRepository jpaRepository;
    private final TournamentParticipantLocalMapper mapper;

    public TournamentParticipantLocalRepositoryAdapter(TournamentParticipantLocalJpaRepository jpaRepository,
                                                        TournamentParticipantLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentParticipantLocal save(TournamentParticipantLocal participant) {
        if (participant == null) {
            return null;
        }
        TournamentParticipantLocalJpaEntity entity = mapper.toEntity(participant);
        TournamentParticipantLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentParticipantLocal> findByTournament(TournamentId tournamentId) {
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
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null || participantId.isBlank()) {
            return;
        }
        jpaRepository.deleteByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}