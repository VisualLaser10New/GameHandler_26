package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link GameJpaEntity}.
 * Gestisce le macchine da gioco fisiche, inclusa la ricerca per edificio
 * e stato, nonché il blocco pessimistico per aggiornamenti concorrenti.
 *
 * @see GameJpaEntity
 * @see GameMachineStatus
 */
@Repository
public interface GameJpaRepository extends JpaRepository<GameJpaEntity, String> {
    /**
     * Recupera tutte le macchine da gioco situate nell'edificio specificato.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return una lista di entità {@link GameJpaEntity} per l'edificio indicato
     */
    List<GameJpaEntity> findByBuildingId(String buildingId);

    /**
     * Recupera tutte le macchine da gioco con lo stato specificato.
     *
     * @param status lo stato della macchina da gioco
     * @return una lista di entità {@link GameJpaEntity} con lo stato indicato
     */
    List<GameJpaEntity> findByStatus(GameMachineStatus status);

    /**
     * Recupera una macchina da gioco per ID con blocco pessimistico in
     * scrittura, prevenendo accessi concorrenti durante l'aggiornamento.
     *
     * @param id l'identificativo univoco della macchina da gioco
     * @return un {@link Optional} contenente l'entità con blocco pessimistico, oppure vuoto se non trovata
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameJpaEntity g where g.id = :id")
    Optional<GameJpaEntity> findByIdForUpdate(@Param("id") String id);
}
