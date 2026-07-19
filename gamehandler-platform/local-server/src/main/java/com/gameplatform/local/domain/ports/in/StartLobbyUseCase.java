package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;

/**
 * Use case per l'avvio della partita da una lobby in attesa. Transita
 * la sessione di gioco dallo stato di lobby a quello di partita attiva,
 * rendendo operativa la sessione per i partecipanti.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface StartLobbyUseCase {
    /**
     * Avvia la partita dalla lobby specificata.
     *
     * @param sessionId identificativo della sessione di gioco della lobby
     * @return la sessione di gioco aggiornata con stato attivo
     */
    GameSession startLobby(GameSessionId sessionId);
}
