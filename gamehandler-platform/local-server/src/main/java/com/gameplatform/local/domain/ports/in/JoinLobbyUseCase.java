package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Use case per l'ingresso di un giocatore in una lobby esistente.
 * Aggiunge l'utente specificato come partecipante alla sessione
 * di gioco, verificando la capienza e lo stato della lobby.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface JoinLobbyUseCase {
    /**
     * Aggiunge l'utente specificato come partecipante alla lobby.
     *
     * @param sessionId identificativo della sessione di gioco della lobby
     * @param userId    identificativo dell'utente da aggiungere
     * @return la sessione di gioco aggiornata con il nuovo partecipante
     */
    GameSession joinLobby(GameSessionId sessionId, UserId userId);
}
