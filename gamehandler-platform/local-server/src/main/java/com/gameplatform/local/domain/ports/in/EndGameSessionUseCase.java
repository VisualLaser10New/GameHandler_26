package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.result.GameResult;

/**
 * Use case per la conclusione di una sessione di gioco attiva.
 * Registra il risultato finale della partita e aggiorna lo stato
 * della sessione a completata, rendendo le statistiche disponibili
 * per le successive consultazioni.
 *
 * @see com.gameplatform.shared.domain.result.GameResult
 */
public interface EndGameSessionUseCase {
    /**
     * Conclude la sessione di gioco con il risultato specificato.
     *
     * @param sessionId identificativo della sessione di gioco da concludere
     * @param result    risultato finale della partita
     */
    void end(GameSessionId sessionId, GameResult result);
}
