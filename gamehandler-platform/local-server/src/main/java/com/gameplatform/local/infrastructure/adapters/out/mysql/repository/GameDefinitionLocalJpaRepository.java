package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameDefinitionLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link GameDefinitionLocalJpaEntity}.
 * Gestisce le definizioni locali dei tipi di gioco, inclusa la ricerca per
 * tipo, l'ordinamento alfabetico e le operazioni di verifica e rimozione
 * basate sul tipo di gioco.
 *
 * @see GameDefinitionLocalJpaEntity
 */
@Repository
public interface GameDefinitionLocalJpaRepository extends JpaRepository<GameDefinitionLocalJpaEntity, String> {

    /**
     * Recupera la definizione di gioco associata al tipo specificato.
     *
     * @param gameType il tipo di gioco (es. "football", "basketball")
     * @return un {@link Optional} contenente l'entità, oppure vuoto se non trovata
     */
    Optional<GameDefinitionLocalJpaEntity> findByGameType(String gameType);

    /**
     * Recupera tutte le definizioni di gioco ordinate per tipo in ordine
     * crescente.
     *
     * @return una lista di tutte le entità {@link GameDefinitionLocalJpaEntity} ordinate per gameType
     */
    List<GameDefinitionLocalJpaEntity> findAllByOrderByGameTypeAsc();

    /**
     * Verifica se esiste una definizione di gioco per il tipo specificato.
     *
     * @param gameType il tipo di gioco da verificare
     * @return {@code true} se esiste una definizione per il tipo, {@code false} altrimenti
     */
    boolean existsByGameType(String gameType);

    /**
     * Elimina la definizione di gioco associata al tipo specificato.
     *
     * @param gameType il tipo di gioco della definizione da eliminare
     */
    @Modifying
    @Query("delete from GameDefinitionLocalJpaEntity d where d.gameType = :gameType")
    void deleteByGameType(@Param("gameType") String gameType);
}