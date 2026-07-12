package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;

@Component
public class TournamentMatchLocalMapper {

    public TournamentMatchLocal toDomain(TournamentMatchLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentMatchLocal(
                new TournamentMatchId(entity.getId()),
                new TournamentId(entity.getTournamentId()),
                entity.getRound(),
                entity.getBracketPosition(),
                entity.getParticipantA(),
                entity.getParticipantB(),
                GameType.valueOf(entity.getGameType()),
                entity.getGameId(),
                TournamentMatchStatus.valueOf(entity.getStatus()),
                entity.getScheduledAt()
        );
    }

    public TournamentMatchLocalJpaEntity toEntity(TournamentMatchLocal domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentMatchLocalJpaEntity(
                domain.getId().value(),
                domain.getTournamentId().value(),
                domain.getRound(),
                domain.getBracketPosition(),
                domain.getParticipantA(),
                domain.getParticipantB(),
                domain.getGameType().name(),
                domain.getGameId(),
                domain.getStatus().name(),
                domain.getScheduledAt()
        );
    }
}