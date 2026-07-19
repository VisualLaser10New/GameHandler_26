package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.game.games.*;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;

/**
 * Factory responsabile della creazione delle istanze di gioco in base al tipo richiesto.
 *
 * <p>Mappa ogni {@link GameType} supportato alla corrispondente implementazione concreta
 * di {@link GameLifecycle}, consentendo la creazione polimorfica di una nuova partita
 * associata a una specifica sessione.</p>
 *
 * @see GameType
 * @see GameLifecycle
 * @see GameSessionId
 */
public class GameFactory {

    /**
     * Crea e restituisce una nuova istanza di gioco del tipo specificato, associata
     * all'identificativo di sessione fornito.
     *
     * <p>Il metodo istanzia l'implementazione concreta di {@link GameLifecycle} corrispondente
     * al {@link GameType} indicato: {@code FOOSBALL}, {@code CHESS}, {@code DARTS},
     * {@code MONOPOLY}, {@code RISK}, {@code SLOT_MACHINE} e {@code ROULETTE}.</p>
     *
     * @param type      il tipo di gioco da creare; non deve essere {@code null}
     * @param sessionId l'identificativo della sessione di gioco a cui associare l'istanza;
     *                  non deve essere {@code null}
     * @return una nuova istanza di {@link GameLifecycle} coerente con il tipo richiesto
     * @throws IllegalArgumentException se {@code type} è {@code null} oppure non corrisponde
     *                                  ad alcun tipo di gioco supportato dalla factory
     * @see GameType
     * @see GameLifecycle
     */
    public static GameLifecycle createGame(GameType type, GameSessionId sessionId) {
        switch (type) {
            case FOOSBALL:
                return new FoosballGame(sessionId);
            case CHESS:
                return new ChessGame(sessionId);
            case DARTS:
                return new DartsGame(sessionId);
            case MONOPOLY:
                return new MonopolyGame(sessionId);
            case RISK:
                return new RiskGame(sessionId);
            case SLOT_MACHINE:
                return new SlotMachineGame(sessionId);
            case ROULETTE:
                return new RouletteGame(sessionId);
            default:
                throw new IllegalArgumentException("Invalid game type: " + type);
        }
    }
}
