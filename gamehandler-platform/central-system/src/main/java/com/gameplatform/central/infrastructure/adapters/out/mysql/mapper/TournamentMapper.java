package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link Tournament} e l'entità persistente {@link TournamentJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte le colonne Stringa
 * {@code game_type}, {@code format} e {@code status} da/verso gli enum
 * {@link GameType}, {@link TournamentFormat} e {@link TournamentStatus},
 * e avvolge gli identificativi {@code id} e {@code created_by} in
 * {@link TournamentId} e {@link UserId}.
 *
 * @see Tournament
 * @see TournamentJpaEntity
 */
@Component
public class TournamentMapper {

    /**
     * Converte un'entità persistente {@link TournamentJpaEntity} nel corrispondente
     * modello di dominio {@link Tournament}.
     * <p>
     * I campi {@code team_based} e {@code team_size} vengono gestiti con
     * protezione {@code null}; {@code team_size} viene impostato a {@code 1}
     * se {@code null}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link Tournament} o {@code null} se l'entità è {@code null}
     * @throws IllegalArgumentException se la stringa {@code game_type}, {@code format} o {@code status} non corrisponde a un valore enum valido
     * @see #toEntity(Tournament)
     */
    public Tournament toDomain(TournamentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Tournament(
                new TournamentId(entity.getId()),
                entity.getName(),
                GameType.valueOf(entity.getGameType()),
                Boolean.TRUE.equals(entity.getTeamBased()),
                entity.getTeamSize() != null ? entity.getTeamSize() : 1,
                TournamentFormat.valueOf(entity.getFormat()),
                TournamentStatus.valueOf(entity.getStatus()),
                entity.getStartsAt(),
                entity.getEndsAt(),
                new UserId(entity.getCreatedBy()),
                entity.getCreatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link Tournament} nell'entità persistente
     * {@link TournamentJpaEntity} da persistere.
     * <p>
     * Gli enum vengono serializzati tramite {@link Enum#name()}.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(TournamentJpaEntity)
     */
    public TournamentJpaEntity toEntity(Tournament domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentJpaEntity(
                domain.getTournamentId().value(),
                domain.getName(),
                domain.getGameType().name(),
                domain.isTeamBased(),
                domain.getTeamSize(),
                domain.getFormat().name(),
                domain.getStatus().name(),
                domain.getStartsAt(),
                domain.getEndsAt(),
                domain.getCreatedBy().value(),
                domain.getCreatedAt()
        );
    }
}