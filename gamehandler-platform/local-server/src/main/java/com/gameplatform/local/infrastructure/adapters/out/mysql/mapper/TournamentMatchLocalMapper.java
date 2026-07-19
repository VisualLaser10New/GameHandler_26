package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;

/**
 * Mapper null-safe tra il modello di dominio {@link TournamentMatchLocal} e
 * l'entità di persistenza {@link TournamentMatchLocalJpaEntity}. Fornisce la
 * conversione bidirezionale per gli incontri di un torneo.
 */
@Component
public class TournamentMatchLocalMapper {

    /**
     * Converte un'entità JPA {@link TournamentMatchLocalJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentMatchLocal}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
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

    /**
     * Converte un modello di dominio {@link TournamentMatchLocal} nella
     * corrispondente entità JPA {@link TournamentMatchLocalJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
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