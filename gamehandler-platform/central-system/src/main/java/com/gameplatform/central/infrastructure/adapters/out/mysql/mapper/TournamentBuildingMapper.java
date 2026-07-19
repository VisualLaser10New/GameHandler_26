package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) per l'entità persistente
 * {@link TournamentBuildingJpaEntity} (tabella di join pura senza un
 * corrispondente modello di dominio centrale).
 * <p>
 * Esposto come bean Spring {@code @Component}, espone un'API basata su
 * stringhe primitive per la trasformazione da/verso la tabella
 * {@code tournament_buildings}.
 *
 * @see TournamentBuildingJpaEntity
 */
@Component
public class TournamentBuildingMapper {

    /**
     * Crea un'entità {@link TournamentBuildingJpaEntity} a partire
     * dagli identificativi del torneo e dell'edificio.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce {@code null}
     * @param buildingId   l'identificativo dell'edificio; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link TournamentBuildingJpaEntity} o {@code null} se uno dei parametri è {@code null}
     * @see #toBuildingId(TournamentBuildingJpaEntity)
     */
    public TournamentBuildingJpaEntity toEntity(String tournamentId, String buildingId) {
        if (tournamentId == null || buildingId == null) {
            return null;
        }
        return new TournamentBuildingJpaEntity(tournamentId, buildingId);
    }

    /**
     * Estrae l'identificativo dell'edificio da un'entità
     * {@link TournamentBuildingJpaEntity}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il valore {@code buildingId} come stringa o {@code null} se l'entità è {@code null}
     * @see #toEntity(String, String)
     */
    public String toBuildingId(TournamentBuildingJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getBuildingId();
    }
}
