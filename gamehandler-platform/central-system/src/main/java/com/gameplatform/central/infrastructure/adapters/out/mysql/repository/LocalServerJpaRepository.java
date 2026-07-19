package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA per l'accesso ai dati dei server locali registrati.
 * <p>
 * Fornisce metodi per interrogare i server attivi, ottenere l'elenco completo
 * ordinato per ultima connessione e disattivare atomicamente un server
 * associato a un edificio.
 * </p>
 *
 * @see RegisteredLocalServerJpaEntity
 */
@Repository
public interface LocalServerJpaRepository extends JpaRepository<RegisteredLocalServerJpaEntity, String> {
    /**
     * Restituisce tutti i server locali attualmente attivi.
     *
     * @return una lista di server locali con stato attivo, vuota se nessun server &egrave; attivo
     */
    List<RegisteredLocalServerJpaEntity> findByIsActiveTrue();

    /**
     * Restituisce tutti i server locali registrati, ordinati dalla data dell'ultima
     * connessione decrescente (dal pi&ugrave; recente al pi&ugrave; vecchio).
     *
     * @return una lista di tutti i server locali ordinati per {@code lastSeenAt} decrescente,
     *         vuota se non ci sono server registrati
     */
    List<RegisteredLocalServerJpaEntity> findAllByOrderByLastSeenAtDesc();

    /**
     * Disattiva atomicamente il server locale associato all'edificio specificato,
     * impostando il flag {@code isActive} a {@code false}.
     *
     * @param buildingId l'identificativo dell'edificio il cui server deve essere disattivato (non null)
     * @return il numero di righe aggiornate (0 se l'edificio non era registrato o era gi&agrave; inattivo)
     */
    @Modifying
    @Query("update RegisteredLocalServerJpaEntity s set s.isActive = false where s.buildingId = :buildingId")
    int deactivateByBuildingId(@Param("buildingId") String buildingId);
}
