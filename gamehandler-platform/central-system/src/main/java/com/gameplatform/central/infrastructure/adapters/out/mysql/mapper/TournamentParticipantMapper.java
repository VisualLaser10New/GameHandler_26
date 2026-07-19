package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link TournamentParticipant} e l'entità persistente
 * {@link TournamentParticipantJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte l'identificativo
 * del torneo da/verso {@link TournamentId} e gestisce la traduzione
 * del campo booleano {@code isTeam} con protezione {@code null}.
 *
 * @see TournamentParticipant
 * @see TournamentParticipantJpaEntity
 */
@Component
public class TournamentParticipantMapper {

    /**
     * Converte un'entità persistente {@link TournamentParticipantJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentParticipant}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link TournamentParticipant} o {@code null} se l'entità è {@code null}
     * @see #toEntity(TournamentParticipant)
     */
    public TournamentParticipant toDomain(TournamentParticipantJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentParticipant(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                Boolean.TRUE.equals(entity.getIsTeam()),
                entity.getDisplayName(),
                entity.getRegisteredAt()
        );
    }

    /**
     * Converte un modello di dominio {@link TournamentParticipant} nell'entità
     * persistente {@link TournamentParticipantJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentParticipantJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(TournamentParticipantJpaEntity)
     */
    public TournamentParticipantJpaEntity toEntity(TournamentParticipant domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentParticipantJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.isTeam(),
                domain.getDisplayName(),
                domain.getRegisteredAt()
        );
    }
}