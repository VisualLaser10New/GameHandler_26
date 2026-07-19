package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Use case per la cancellazione di una lobby di gioco esistente.
 * Rimuove la lobby identificata dall'ID di sessione, verificando
 * che l'utente richiedente sia autorizzato all'operazione.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface CancelLobbyUseCase {
    /**
     * Cancella la lobby corrispondente all'ID di sessione specificato.
     *
     * @param sessionId identificativo della sessione di gioco della lobby
     * @param userId    identificativo dell'utente che richiede la cancellazione
     * @return la sessione di gioco aggiornata dopo la cancellazione della lobby
     */
    GameSession cancelLobby(GameSessionId sessionId, UserId userId);
}
