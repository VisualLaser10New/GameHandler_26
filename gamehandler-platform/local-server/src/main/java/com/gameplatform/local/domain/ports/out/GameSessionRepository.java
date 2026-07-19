package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la gestione delle sessioni di gioco.
 * <p>
 * Fornisce operazioni CRUD e di ricerca per le sessioni di gioco svolte
 * sulle macchine della sede locale. Supporta la ricerca per edificio,
 * tipo di gioco, stato e partecipante, nonch&eacute; la gestione delle
 * sessioni in attesa di sincronizzazione.
 * </p>
 *
 * @see GameSession
 * @see GameStatus
 */
public interface GameSessionRepository {
    /**
     * Salva o aggiorna una sessione di gioco.
     *
     * @param session la sessione di gioco da persistere
     * @return la sessione di gioco persistita
     */
    GameSession save(GameSession session);

    /**
     * Cerca una sessione di gioco in base al suo identificativo.
     *
     * @param id l'identificativo della sessione di gioco
     * @return un {@code Optional} contenente la sessione di gioco, vuoto se non trovata
     */
    Optional<GameSession> findById(GameSessionId id);

    /**
     * Restituisce tutte le sessioni di gioco svolte in un determinato edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return la lista delle sessioni di gioco nell'edificio specificato
     */
    List<GameSession> findByBuildingId(BuildingId buildingId);

    /**
     * Restituisce tutte le sessioni di gioco per un determinato tipo di gioco.
     *
     * @param gameType il tipo di gioco da filtrare
     * @return la lista delle sessioni di gioco del tipo specificato
     */
    List<GameSession> findByGameType(GameType gameType);

    /**
     * Restituisce tutte le sessioni di gioco con un determinato stato.
     *
     * @param status lo stato delle sessioni da filtrare
     * @return la lista delle sessioni di gioco con lo stato specificato
     */
    List<GameSession> findByStatus(GameStatus status);

    /**
     * Restituisce tutte le sessioni di gioco in attesa di sincronizzazione
     * con il sistema centrale.
     *
     * @return la lista delle sessioni in attesa di sincronizzazione
     */
    List<GameSession> findPendingSync();

    /**
     * Cerca una sessione di gioco attiva per una determinata macchina da gioco.
     *
     * @param gameId l'identificativo della macchina da gioco
     * @return un {@code Optional} contenente la sessione attiva, vuoto se non trovata
     */
    Optional<GameSession> findActiveByGameId(GameId gameId);

    /**
     * Restituisce tutte le sessioni di gioco a cui ha partecipato un determinato
     * utente, indipendentemente dallo stato della sessione.
     *
     * @param userId l'identificativo dell'utente partecipante
     * @return la lista delle sessioni di gioco dell'utente specificato
     */
    List<GameSession> findByParticipant(UserId userId);
}
