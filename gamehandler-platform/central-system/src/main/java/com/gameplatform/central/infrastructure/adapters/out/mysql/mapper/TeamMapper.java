package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper senza stato (null-safe) per il modello di dominio centrale
 * {@link Team} e le entità persistenti {@link TournamentTeamJpaEntity}
 * e {@link TournamentTeamMemberJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, gestisce la conversione
 * dei membri del team da/verso una lista di {@link UserId}, assorbendo
 * la logica di join tra le tabelle {@code tournament_teams} e
 * {@code tournament_team_members}.
 *
 * @see Team
 * @see TournamentTeamJpaEntity
 * @see TournamentTeamMemberJpaEntity
 */
@Component
public class TeamMapper {

    /**
     * Converte un'entità {@link TournamentTeamJpaEntity} e la relativa lista
     * di membri {@link TournamentTeamMemberJpaEntity} nel corrispondente
     * modello di dominio {@link Team}.
     * <p>
     * Se la lista dei membri è {@code null} viene restituita una lista vuota.
     *
     * @param team    l'entità persistente del team; se {@code null} restituisce {@code null}
     * @param members la lista delle entità dei membri del team; se {@code null} viene trattata come lista vuota
     * @return il modello di dominio {@link Team} o {@code null} se il team è {@code null}
     * @see #toTeamEntity(Team)
     * @see #toMemberEntities(Team)
     */
    public Team toDomain(TournamentTeamJpaEntity team, List<TournamentTeamMemberJpaEntity> members) {
        if (team == null) {
            return null;
        }
        List<UserId> memberIds = (members == null)
                ? List.of()
                : members.stream().map(m -> new UserId(m.getUserId())).toList();
        return new Team(
                new TeamId(team.getId()),
                new TournamentId(team.getTournamentId()),
                team.getName(),
                memberIds,
                team.getCreatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link Team} nell'entità persistente
     * {@link TournamentTeamJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentTeamJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(TournamentTeamJpaEntity, List)
     * @see #toMemberEntities(Team)
     */
    public TournamentTeamJpaEntity toTeamEntity(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentTeamJpaEntity(
                domain.getTeamId().value(),
                domain.getTournamentId().value(),
                domain.getName(),
                domain.getCreatedAt()
        );
    }

    /**
     * Converte i membri di un modello di dominio {@link Team} in una lista
     * di entità {@link TournamentTeamMemberJpaEntity} da persistere.
     * <p>
     * Se il dominio è {@code null} restituisce una lista vuota.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce una lista vuota
     * @return una lista di entità {@link TournamentTeamMemberJpaEntity}, mai {@code null}
     * @see #toDomain(TournamentTeamJpaEntity, List)
     * @see #toTeamEntity(Team)
     */
    public List<TournamentTeamMemberJpaEntity> toMemberEntities(Team domain) {
        if (domain == null) {
            return List.of();
        }
        return domain.getMembers().stream()
                .map(m -> new TournamentTeamMemberJpaEntity(domain.getTeamId().value(), m.value()))
                .toList();
    }
}