package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.GameDefinitionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati delle definizioni dei giochi.
 * <p>
 * Fornisce metodi per interrogare, verificare l'esistenza ed eliminare
 * definizioni di gioco in base al loro tipo. L'ordinamento alfabetico per tipo
 * di gioco &egrave; garantito dalla query di elenco completo.
 * </p>
 *
 * @see GameDefinitionJpaEntity
 */
@Repository
public interface GameDefinitionJpaRepository extends JpaRepository<GameDefinitionJpaEntity, String> {

    /**
     * Restituisce la definizione del gioco associata al tipo specificato, se presente.
     *
     * @param gameType il tipo di gioco (non null)
     * @return un {@code Optional} contenente la definizione del gioco se trovata, vuoto altrimenti
     */
    Optional<GameDefinitionJpaEntity> findByGameType(String gameType);

    /**
     * Restituisce tutte le definizioni dei giochi ordinate per tipo di gioco in ordine crescente.
     *
     * @return una lista di tutte le definizioni dei giochi ordinate alfabeticamente per tipo,
     *         vuota se non ci sono definizioni
     */
    List<GameDefinitionJpaEntity> findAllByOrderByGameTypeAsc();

    /**
     * Verifica se esiste una definizione per il tipo di gioco specificato.
     *
     * @param gameType il tipo di gioco da verificare (non null)
     * @return {@code true} se esiste una definizione per il tipo specificato, {@code false} altrimenti
     */
    boolean existsByGameType(String gameType);

    /**
     * Elimina la definizione del gioco associata al tipo specificato.
     *
     * @param gameType il tipo di gioco della definizione da eliminare (non null)
     */
    @Modifying
    @Query("delete from GameDefinitionJpaEntity d where d.gameType = :gameType")
    void deleteByGameType(@Param("gameType") String gameType);
}
