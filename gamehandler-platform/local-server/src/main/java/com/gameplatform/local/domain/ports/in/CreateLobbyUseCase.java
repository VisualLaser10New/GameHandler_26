package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Use case per la creazione di una nuova lobby di gioco.
 * Inizializza una sessione di gioco associata a un gioco specifico,
 * con il tipo di gioco indicato e con l'utente creatore come primo
 * partecipante.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface CreateLobbyUseCase {
    /**
     * Crea una nuova lobby per il gioco e il tipo specificati.
     *
     * @param gameId    identificativo del gioco da associare alla lobby
     * @param gameType  tipo di gioco per la lobby
     * @param creatorId identificativo dell'utente creatore della lobby
     * @return la sessione di gioco rappresentante la lobby creata
     */
    GameSession createLobby(GameId gameId, GameType gameType, UserId creatorId);
}
