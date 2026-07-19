package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

/**
 * Use case per l'avvio di una nuova sessione di gioco. Inizializza una
 * partita per il gioco e il tipo specificati, con l'elenco dei partecipanti
 * e l'eventuale prenotazione associata.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface StartGameSessionUseCase {
    /**
     * Avvia una nuova sessione di gioco con i parametri specificati.
     *
     * @param gameId        identificativo del gioco da avviare
     * @param gameType      tipo di gioco
     * @param participants  elenco dei partecipanti alla sessione
     * @param reservationId identificativo della prenotazione associata, oppure null
     * @return la sessione di gioco avviata
     */
    GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId);
}
