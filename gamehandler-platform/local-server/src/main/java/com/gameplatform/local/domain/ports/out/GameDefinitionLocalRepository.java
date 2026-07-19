package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.shared.domain.model.GameType;

import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la gestione delle definizioni locali dei giochi.
 * <p>
 * Fornisce operazioni CRUD per le definizioni di gioco memorizzate localmente,
 * indicizzate per tipo di gioco. Ciascuna definizione contiene la configurazione
 * specifica necessaria per avviare e gestire le partite di un determinato tipo.
 * </p>
 */
public interface GameDefinitionLocalRepository {
    /**
     * Salva o aggiorna una definizione di gioco locale.
     *
     * @param gameDefinitionLocal la definizione di gioco da persistere
     * @return la definizione di gioco persistita
     */
    GameDefinitionLocal save(GameDefinitionLocal gameDefinitionLocal);

    /**
     * Cerca una definizione di gioco in base al tipo.
     *
     * @param gameType il tipo di gioco da ricercare
     * @return un {@code Optional} contenente la definizione di gioco, vuoto se non trovata
     */
    Optional<GameDefinitionLocal> findByGameType(GameType gameType);

    /**
     * Restituisce tutte le definizioni di gioco locali.
     *
     * @return la lista completa delle definizioni di gioco
     */
    List<GameDefinitionLocal> findAll();

    /**
     * Verifica se esiste una definizione di gioco per il tipo specificato.
     *
     * @param gameType il tipo di gioco da verificare
     * @return {@code true} se esiste una definizione per il tipo specificato, {@code false} altrimenti
     */
    boolean existsByGameType(GameType gameType);

    /**
     * Elimina la definizione di gioco associata al tipo specificato.
     *
     * @param gameType il tipo di gioco della definizione da eliminare
     */
    void deleteByGameType(GameType gameType);
}
