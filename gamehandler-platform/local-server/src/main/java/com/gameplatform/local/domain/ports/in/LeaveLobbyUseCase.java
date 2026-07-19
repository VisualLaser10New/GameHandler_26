package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Use case per l'uscita di un giocatore da una lobby esistente.
 * Rimuove l'utente specificato dalla lista dei partecipanti della
 * sessione di gioco.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface LeaveLobbyUseCase {
    /**
     * Rimuove l'utente specificato dalla lobby.
     *
     * @param sessionId identificativo della sessione di gioco della lobby
     * @param userId    identificativo dell'utente da rimuovere
     * @return la sessione di gioco aggiornata senza il partecipante rimosso
     */
    GameSession leaveLobby(GameSessionId sessionId, UserId userId);
}
