package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;

/**
 * Use case per la ripresa di una sessione di gioco precedentemente sospesa.
 * Ripristina lo stato di gioco salvato e riattiva la partita per i
 * partecipanti.
 */
public interface ResumeGameSessionUseCase {
    /**
     * Riprende la sessione di gioco specificata dopo una sospensione.
     *
     * @param sessionId identificativo della sessione di gioco da riprendere
     */
    void resume(GameSessionId sessionId);
}
