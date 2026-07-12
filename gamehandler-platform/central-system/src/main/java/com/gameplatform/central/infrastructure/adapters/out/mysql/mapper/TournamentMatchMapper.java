package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link TournamentMatch} central domain model and
 * the {@link TournamentMatchJpaEntity} persistence entity
 * ({@code tournament_matches} table, FASE 4 PIANO &sect;3.4). {@code @Component}
 * instance bean (matches {@code GameDefinitionMapper}/{@code PlayerStatisticsMapper});
 * converts the {@code status} String column to/from
 * {@link TournamentMatchStatus} and wraps the {@code id}/{@code tournament_id}
 * columns to/from {@link TournamentMatchId}/{@link TournamentId} at the
 * boundary. Boxed primitives ({@code round}/{@code bracket_position}) are
 * null-safe-defaulted to {@code 0} on the domain side (mirroring
 * {@code PlayerStatisticsMapper}); the nullable FASE 5/6 columns
 * ({@code participant_b}, {@code building_id}, {@code game_id},
 * {@code session_id}, {@code winner}, {@code scheduled_at}, {@code played_at},
 * {@code result_data}) are preserved as-is. Per the Module Plan &sect;4.1 spec,
 * the match has no {@code game_type} field (it lives on the parent
 * {@code Tournament}).
 */
@Component
public class TournamentMatchMapper {

    public TournamentMatch toDomain(TournamentMatchJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentMatch(
                new TournamentMatchId(entity.getId()),
                new TournamentId(entity.getTournamentId()),
                entity.getRound() != null ? entity.getRound() : 0,
                entity.getBracketPosition() != null ? entity.getBracketPosition() : 0,
                entity.getParticipantA(),
                entity.getParticipantB(),
                entity.getBuildingId(),
                entity.getGameId(),
                entity.getSessionId(),
                entity.getWinner(),
                TournamentMatchStatus.valueOf(entity.getStatus()),
                entity.getScheduledAt(),
                entity.getPlayedAt(),
                entity.getResultData()
        );
    }

    public TournamentMatchJpaEntity toEntity(TournamentMatch domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentMatchJpaEntity(
                domain.getMatchId().value(),
                domain.getTournamentId().value(),
                domain.getRound(),
                domain.getBracketPosition(),
                domain.getParticipantA(),
                domain.getParticipantB(),
                domain.getBuildingId(),
                domain.getGameId(),
                domain.getSessionId(),
                domain.getWinner(),
                domain.getStatus().name(),
                domain.getScheduledAt(),
                domain.getPlayedAt(),
                domain.getResultData()
        );
    }
}