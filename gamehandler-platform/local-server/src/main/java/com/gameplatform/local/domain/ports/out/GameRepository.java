package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la gestione delle macchine da gioco locali.
 * <p>
 * Fornisce operazioni CRUD per le entit&agrave; {@code Game} che rappresentano
 * le postazioni fisiche di gioco (macchine) presenti nella sede locale. Ogni
 * macchina &egrave; associata a un edificio e a uno stato operativo.
 * </p>
 */
public interface GameRepository {
    /**
     * Salva o aggiorna una macchina da gioco.
     *
     * @param game la macchina da gioco da persistere
     * @return la macchina da gioco persistita
     */
    Game save(Game game);

    /**
     * Cerca una macchina da gioco in base al suo identificativo.
     *
     * @param id l'identificativo della macchina da gioco
     * @return un {@code Optional} contenente la macchina da gioco, vuoto se non trovata
     */
    Optional<Game> findById(GameId id);

    /**
     * Cerca una macchina da gioco con blocco pessimistico per aggiornamento.
     *
     * @param id l'identificativo della macchina da gioco
     * @return un {@code Optional} contenente la macchina da gioco bloccata, vuoto se non trovata
     */
    Optional<Game> findByIdForUpdate(GameId id);

    /**
     * Restituisce tutte le macchine da gioco appartenenti a un determinato edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return la lista delle macchine da gioco nell'edificio specificato
     */
    List<Game> findByBuildingId(BuildingId buildingId);

    /**
     * Restituisce tutte le macchine da gioco con un determinato stato operativo.
     *
     * @param status lo stato operativo da filtrare
     * @return la lista delle macchine da gioco con lo stato specificato
     */
    List<Game> findByStatus(GameMachineStatus status);

    /**
     * Restituisce tutte le macchine da gioco presenti nel sistema locale.
     *
     * @return la lista completa delle macchine da gioco
     */
    List<Game> findAll();

    /**
     * Elimina una macchina da gioco in base al suo identificativo.
     *
     * @param id l'identificativo della macchina da gioco da eliminare
     */
    void deleteById(GameId id);
}
