package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;

import java.util.Optional;

/**
 * Use case per la lettura di una lobby attiva associata a un gioco.
 * Restituisce la sessione di gioco in corso per il gioco specificato,
 * se presente.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface GetActiveLobbyUseCase {
    /**
     * Restituisce la lobby attiva per il gioco specificato, se esistente.
     *
     * @param gameId identificativo del gioco di cui cercare la lobby attiva
     * @return un {@code Optional} contenente la sessione di gioco attiva, oppure vuoto
     */
    Optional<GameSession> getActiveLobby(GameId gameId);
}
