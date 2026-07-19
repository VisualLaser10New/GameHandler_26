package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA per l'accesso ai dati di associazione tra tornei ed edifici.
 * <p>
 * Fornisce metodi per interrogare gli edifici associati a un torneo, verificare
 * l'esistenza di una specifica associazione ed eliminare tutte le associazioni
 * di un torneo.
 * </p>
 *
 * @see TournamentBuildingJpaEntity
 * @see TournamentBuildingId
 */
@Repository
public interface TournamentBuildingJpaRepository extends JpaRepository<TournamentBuildingJpaEntity, TournamentBuildingId> {

    /**
     * Restituisce tutte le associazioni torneo-edificio per il torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di associazioni torneo-edificio, vuota se il torneo non ha edifici associati
     */
    List<TournamentBuildingJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Verifica se esiste un'associazione tra il torneo e l'edificio specificati.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @param buildingId   l'identificativo dell'edificio (non null)
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     */
    boolean existsByTournamentIdAndBuildingId(String tournamentId, String buildingId);

    /**
     * Elimina tutte le associazioni torneo-edificio per il torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo di cui rimuovere le associazioni (non null)
     */
    @Modifying
    @Query("delete from TournamentBuildingJpaEntity b where b.tournamentId = :tournamentId")
    void deleteByTournamentId(@Param("tournamentId") String tournamentId);
}