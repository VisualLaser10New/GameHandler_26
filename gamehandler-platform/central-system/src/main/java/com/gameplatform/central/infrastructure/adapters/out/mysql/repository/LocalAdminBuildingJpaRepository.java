package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA per l'accesso ai dati di associazione tra amministratori locali ed edifici.
 * <p>
 * Fornisce metodi per interrogare gli edifici associati a un utente, verificare
 * l'esistenza di una specifica associazione ed eliminare un'associazione.
 * </p>
 *
 * @see LocalAdminBuildingJpaEntity
 * @see LocalAdminBuildingId
 */
@Repository
public interface LocalAdminBuildingJpaRepository extends JpaRepository<LocalAdminBuildingJpaEntity, LocalAdminBuildingId> {

    /**
     * Restituisce tutte le associazioni amministratore-edificio per l'utente specificato.
     *
     * @param userId l'identificativo dell'utente amministratore (non null)
     * @return una lista di associazioni amministratore-edificio, vuota se l'utente non ha edifici associati
     */
    List<LocalAdminBuildingJpaEntity> findByUserId(String userId);

    /**
     * Verifica se esiste un'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'identificativo dell'utente (non null)
     * @param buildingId l'identificativo dell'edificio (non null)
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti
     */
    boolean existsByUserIdAndBuildingId(String userId, String buildingId);

    /**
     * Elimina l'associazione tra l'utente e l'edificio specificati.
     *
     * @param userId     l'identificativo dell'utente (non null)
     * @param buildingId l'identificativo dell'edificio (non null)
     */
    @Modifying
    @Query("delete from LocalAdminBuildingJpaEntity b where b.userId = :userId and b.buildingId = :buildingId")
    void deleteByUserIdAndBuildingId(@Param("userId") String userId, @Param("buildingId") String buildingId);
}