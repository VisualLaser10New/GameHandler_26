package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.ports.out.TeamMembersLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TeamMemberLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the {@link TeamMembersLocalRepository} port (BUG-TEAM-3).
 * {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code teamId}, {@code userId});
 * {@code deleteByTournament} removes the full per-tournament team→user
 * membership snapshot in one bulk delete — used by the sync service's
 * full-snapshot replace (delete+insert idempotency). The entity is a pure
 * 3-field join table, so the adapter writes the entity directly without a
 * domain-model + mapper layer (simpler than
 * {@code TournamentParticipantLocalRepositoryAdapter}).
 */
@Component
public class TeamMembersLocalRepositoryAdapter implements TeamMembersLocalRepository {

    private final TeamMemberLocalJpaRepository jpaRepository;

    public TeamMembersLocalRepositoryAdapter(TeamMemberLocalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(String tournamentId, String teamId, String userId) {
        if (tournamentId == null || tournamentId.isBlank()
                || teamId == null || teamId.isBlank()
                || userId == null || userId.isBlank()) {
            return;
        }
        jpaRepository.save(new TeamMemberLocalJpaEntity(tournamentId, teamId, userId));
    }

    @Override
    @Transactional
    public void deleteByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepository.deleteByTournamentId(tournamentId.value());
    }
}