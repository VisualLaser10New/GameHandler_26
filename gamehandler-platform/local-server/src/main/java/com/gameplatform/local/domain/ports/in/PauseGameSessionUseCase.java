package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;

/**
 * Use case per la sospensione temporanea di una sessione di gioco attiva.
 * Mette in pausa la partita in corso, consentendo la successiva ripresa
 * senza perdita dello stato di gioco.
 */
public interface PauseGameSessionUseCase {
    /**
     * Sospende la sessione di gioco specificata.
     *
     * @param sessionId identificativo della sessione di gioco da sospendere
     */
    void pause(GameSessionId sessionId);
}
