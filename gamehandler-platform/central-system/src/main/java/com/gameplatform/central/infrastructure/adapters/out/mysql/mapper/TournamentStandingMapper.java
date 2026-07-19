package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di lettura centralizzato
 * {@link TournamentStanding} e l'entità persistente {@link TournamentStandingJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte l'identificativo
 * del torneo da/verso {@link TournamentId} e applica valori predefiniti
 * ({@code 0}) ai campi numerici {@code wins}, {@code losses} e {@code points}
 * quando {@code null} sul lato entità.
 *
 * @see TournamentStanding
 * @see TournamentStandingJpaEntity
 */
@Component
public class TournamentStandingMapper {

    /**
     * Converte un'entità persistente {@link TournamentStandingJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentStanding}.
     * <p>
     * I campi {@code wins}, {@code losses} e {@code points} vengono impostati a
     * {@code 0} se {@code null} nell'entità.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link TournamentStanding} o {@code null} se l'entità è {@code null}
     * @see #toEntity(TournamentStanding)
     */
    public TournamentStanding toDomain(TournamentStandingJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentStanding(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                entity.getWins() != null ? entity.getWins() : 0,
                entity.getLosses() != null ? entity.getLosses() : 0,
                entity.getPoints() != null ? entity.getPoints() : 0,
                entity.getRank()
        );
    }

    /**
     * Converte un modello di dominio {@link TournamentStanding} nell'entità
     * persistente {@link TournamentStandingJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentStandingJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(TournamentStandingJpaEntity)
     */
    public TournamentStandingJpaEntity toEntity(TournamentStanding domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentStandingJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.getWins(),
                domain.getLosses(),
                domain.getPoints(),
                domain.getRank()
        );
    }
}