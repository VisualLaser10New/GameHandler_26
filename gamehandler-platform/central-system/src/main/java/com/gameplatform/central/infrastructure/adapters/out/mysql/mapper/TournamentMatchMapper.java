package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link TournamentMatch} e l'entità persistente {@link TournamentMatchJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte gli identificativi
 * da/verso {@link TournamentMatchId} e {@link TournamentId}, traduce lo
 * {@code status} Stringa da/verso {@link TournamentMatchStatus} e applica
 * valori predefiniti ({@code 0}) ai campi {@code round} e
 * {@code bracket_position} quando {@code null} nell'entità.
 *
 * @see TournamentMatch
 * @see TournamentMatchJpaEntity
 */
@Component
public class TournamentMatchMapper {

    /**
     * Converte un'entità persistente {@link TournamentMatchJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentMatch}.
     * <p>
     * I campi {@code round} e {@code bracket_position} vengono impostati a
     * {@code 0} se {@code null} nell'entità; lo {@code status} viene convertito
     * tramite {@link TournamentMatchStatus#valueOf(String)}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link TournamentMatch} o {@code null} se l'entità è {@code null}
     * @throws IllegalArgumentException se la stringa {@code status} non corrisponde a un valore {@link TournamentMatchStatus} valido
     * @see #toEntity(TournamentMatch)
     */
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

    /**
     * Converte un modello di dominio {@link TournamentMatch} nell'entità
     * persistente {@link TournamentMatchJpaEntity} da persistere.
     * <p>
     * Lo {@link TournamentMatchStatus} viene serializzato tramite
     * {@link Enum#name()}.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentMatchJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(TournamentMatchJpaEntity)
     */
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