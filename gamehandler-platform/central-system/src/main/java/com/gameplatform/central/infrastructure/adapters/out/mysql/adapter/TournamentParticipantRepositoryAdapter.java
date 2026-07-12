package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentParticipantMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentParticipantJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentParticipantRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape: constructor-injects the JPA repository + mapper; writes carry the default
 * {@code @Transactional} propagation and reads are marked
 * {@code @Transactional(readOnly = true)}. All read paths are null-safe, returning
 * {@code Optional.empty()} / {@code List.of()} / {@code false} / {@code 0L} when
 * their arguments are {@code null}.
 */
@Component
public class TournamentParticipantRepositoryAdapter implements TournamentParticipantRepository {

    private final TournamentParticipantJpaRepository jpaRepo;
    private final TournamentParticipantMapper mapper;

    public TournamentParticipantRepositoryAdapter(TournamentParticipantJpaRepository jpaRepo,
                                                  TournamentParticipantMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TournamentParticipant save(TournamentParticipant participant) {
        TournamentParticipantJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(participant));
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentParticipant> findByTournamentAndParticipantId(TournamentId tournamentId,
                                                                             String participantId) {
        if (tournamentId == null || participantId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndParticipantId(tournamentId.value(), participantId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentParticipant> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentParticipantJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return 0L;
        }
        return jpaRepo.countByTournamentId(tournamentId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return false;
        }
        return jpaRepo.existsByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }

    @Override
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return;
        }
        jpaRepo.deleteByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}