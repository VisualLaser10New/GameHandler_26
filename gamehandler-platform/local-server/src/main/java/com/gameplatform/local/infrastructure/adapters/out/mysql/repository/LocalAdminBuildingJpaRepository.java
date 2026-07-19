package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link LocalAdminBuildingJpaEntity}.
 * Gestisce l'associazione molti-a-molti tra utenti amministratori locali e
 * edifici, con chiave primaria composita {@link LocalAdminBuildingId}.
 * Fornisce operazioni di ricerca, verifica di esistenza e rimozione delle
 * associazioni.
 *
 * @see LocalAdminBuildingJpaEntity
 * @see LocalAdminBuildingId
 */
@Repository
public interface LocalAdminBuildingJpaRepository extends JpaRepository<LocalAdminBuildingJpaEntity, LocalAdminBuildingId> {

    /**
     * Recupera tutte le associazioni amministratore-edificio per un
     * determinato ID utente.
     *
     * @param userId l'ID dell'utente amministratore
     * @return una lista di entità {@link LocalAdminBuildingJpaEntity} per l'utente indicato
     */
    List<LocalAdminBuildingJpaEntity> findByUserId(String userId);

    /**
     * Verifica se esiste un'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'ID dell'utente amministratore
     * @param buildingId l'ID dell'edificio
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     */
    boolean existsByUserIdAndBuildingId(String userId, String buildingId);

    /**
     * Elimina l'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'ID dell'utente amministratore
     * @param buildingId l'ID dell'edificio da dissociare
     */
    @Modifying
    @Query("delete from LocalAdminBuildingJpaEntity b where b.userId = :userId and b.buildingId = :buildingId")
    void deleteByUserIdAndBuildingId(@Param("userId") String userId, @Param("buildingId") String buildingId);
}