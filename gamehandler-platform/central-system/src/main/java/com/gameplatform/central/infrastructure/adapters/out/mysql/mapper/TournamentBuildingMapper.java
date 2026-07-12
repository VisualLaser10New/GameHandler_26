package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper for the {@link TournamentBuildingJpaEntity} persistence
 * entity ({@code tournament_buildings} table, FASE 4 PIANO &sect;3.1) &mdash;
 * a pure join-table with no central domain POJO of its own. {@code @Component}
 * instance bean (matches {@code LocalAdminBuildingMapper}); exposes a primitive
 * String API at the boundary since the table is queried only for its
 * {@code building_id} values by {@code TournamentQueryService}.
 */
@Component
public class TournamentBuildingMapper {

    public TournamentBuildingJpaEntity toEntity(String tournamentId, String buildingId) {
        if (tournamentId == null || buildingId == null) {
            return null;
        }
        return new TournamentBuildingJpaEntity(tournamentId, buildingId);
    }

    public String toBuildingId(TournamentBuildingJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getBuildingId();
    }
}
